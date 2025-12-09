package replayuploader

import evolutioncomplete.WinnerShared.*
import evolutioncomplete._
import evolutioncomplete.GameStateShared.*

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import sttp.client4.*
import sttp.model.Part
import fetch.FetchBackend

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import com.thoughtworks.binding.Binding
import Binding.Var
import com.yang_bo.html.*
import upickle.default.*
import com.thoughtworks.binding.FutureBinding
import org.scalajs.dom.html.{Button, Div, Input, Select, Form}
import com.thoughtworks.binding.Binding.Constants
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.FormData
import org.scalajs.dom.File

object GenericReplayUploader {
  def initTournament(
      matchId: Long,
      tournamentId: Long,
      containerID: String
  ): Unit = {
    val metaSession = TournamentMetaSession(matchId, tournamentId)
    val replayUploader = new GenericReplayUploader(metaSession)
    replayUploader.init(containerID)
  }

  def initCasualMatch(casualMatchId: Long, containerID: String): Unit = {
    val metaSession = CasualMatchMetaSession(casualMatchId)
    val replayUploader = new GenericReplayUploader(metaSession)
    replayUploader.init(containerID)
  }
}
class GenericReplayUploader[SS <: TUploadStateShared[SS]](
    metaSession: MetaSessionShared[SS]
) {

  val uploadState = Var[SS](metaSession.default())

  def init(containerID: String): Unit = {

    val container = org.scalajs.dom.document.getElementById(containerID)
    render(container, uploadDivision(uploadState))
    fetchState().onComplete {
      case Success(Right(value)) => uploadState.value = value
      case Success(Left(error))  => uploadState.value = metaSession.error()
      case Failure(error)        => uploadState.value = metaSession.error()
    }
  }

  def fetchState(): Future[Either[String, SS]] = {

    val request = basicRequest
      // send the body as form data (x-www-form-urlencoded)
      .headers(Map("Csrf-Token" -> Main.findTokenValue()))
      // use an optional parameter in the URI
      .get(metaSession.fetchStateUri)

    val backend = FetchBackend()
    request.send(backend).map {
      case Response(
            Right(jsonResponse),
            sttp.model.StatusCode.Ok,
            _,
            _,
            _,
            _
          ) =>
        Try(metaSession.readJson(jsonResponse)) match {
          case Success(valid) => Right(valid)
          case Failure(error) => Left(error.getLocalizedMessage)
        }
      case error => Left(error.toString)
    }
  }

  def postState(
      validFiles: List[File],
      parameterUploadState: SS
  ): Future[Either[String, SS]] = {

    // Create file parts
    val fileParts = validFiles.map { file =>
      multipartFile("replays", file).fileName(file.name)
    }

    // Create JSON state part
    val statePart =
      multipart("state", metaSession.writeJson(parameterUploadState))
        .contentType(
          "application/json"
        )

    // Combine all parts
    val allParts = fileParts :+ statePart

    val request = basicRequest
      .multipartBody(allParts)
      .headers(Map("Csrf-Token" -> Main.findTokenValue()))
      .post(metaSession.updateStateUri)

    val backend = FetchBackend()
    request.send(backend).map {
      case Response(
            Right(jsonResponse),
            sttp.model.StatusCode.Ok,
            _,
            _,
            _,
            _
          ) =>
        Try(metaSession.readJson(jsonResponse)) match {
          case Success(valid) => Right(valid)
          case Failure(error) => Left(error.getLocalizedMessage)
        }
      case error => Left(error.toString)
    }
  }

  def removeFile(uuid: UUID): Unit = {
    uploadState.value.updateToPending(uuid)
    val request = basicRequest
      .headers(Map("Csrf-Token" -> Main.findTokenValue()))
      .post(metaSession.removeFileUri(uuid))
    val backend = FetchBackend()
    val response = request.send(backend).map {
      case Response(
            Right(jsonResponse),
            sttp.model.StatusCode.Ok,
            _,
            _,
            _,
            _
          ) =>
        Try(metaSession.readJson(jsonResponse)) match {
          case Success(valid) => Right(valid)
          case Failure(error) => Left(error.getLocalizedMessage)
        }
      case error => Left(error.toString)
    }
    response.onComplete {
      case Success(Right(value)) => uploadState.value = value
      case Success(Left(error))  => println(error)
      case Failure(error)        => println(error)
    }
  }

  def uploadDivision(currentState: Binding[SS]): Binding[Div] = {
    html"""<div id="match_result">
         <div class="games-list">
           <h1 class="game-row">
           <span class="player_left">${currentState.bind.firstParticipant.userName}</span>
           <span class="vs">vs</span>
           <span class="player_right">${currentState.bind.secondParticipant.userName}</span>
         </h1>
         <h6>Replays</h6>
        ${for (game <- currentState.bind.games) yield {
        html"""<div class="game-row">${game match {
            case ValidGame(smurfs, mapName, playedAt, hash, uuid) =>
              html"""<span class="status-icon success">✓</span>
                          <span class="player_left">${currentState.bind
                  .getFirstSmurf(game)}</span>
                          <span class="vs">vs</span>
                          <span class="player_right">${currentState.bind
                  .getSecondSmurf(game)}</span>
                          <button class="delete-button outline contrast error" onclick=${(_: Event) =>
                  removeFile(uuid)}>&#x1F5D1;</button>"""
            case PendingGame(_) =>
              html"""<span class="status-icon pending">⌛</span>
                                  <span class="inner_space"><progress></progress></span>
                                  <span class="delete-button"></span>
                                  """
            case InvalidGame(errorMessage, uuid) =>
              html"""<span class="status-icon error">◯</span>
                        <span class="inner_space error-text">$errorMessage</span>
                        <button class="delete-button outline contrast error" onclick=${(_: Event) =>
                  removeFile(uuid)}>&#x1F5D1;</button>"""
          }}</div>"""
      }}
        <label for="file-input" class="secondary add_more_replays" role="button">
          ${
        val input: Binding.Stable[Input] =
          html"""<input type="file" id="file-input" hidden multiple>"""
        input.value.onchange = (_: Event) => {
          val (validFiles, invalidFiles) = input.value.files.toList.partition(
            f => f.size <= 1024 * 1024 && f.name.endsWith(".rep")
          )
          val invalidFilesReason = invalidFiles.map { f =>
            if (f.size > 1024 * 1024)
              InvalidGame("Archivo > 1Mb", UUID.randomUUID())
            else InvalidGame("Archivo no es *.rep", UUID.randomUUID())
          }

          val pendingFiles = validFiles.map(_ => PendingGame(UUID.randomUUID()))
          uploadState.value = uploadState.value.withExtraGames(
            pendingFiles ::: invalidFilesReason
          )
          postState(validFiles, uploadState.value).onComplete {
            case Success(Right(value)) => uploadState.value = value
            case Success(Left(error))  => println(error)
            case Failure(error)        => println(error)
          }
        }
        input
      }
          + Agregar replays
        </label> </div>
        <div class="grid">
        <article>
      <header>Relaciona smurfs/nicks con los jugadores</header>
          ${Constants(currentState.bind.getSmurfs*).flatMap { smurfSelection =>
        html"""<fieldset>
                          <span class="status-icon">Smurf en la partida <b>${smurfSelection.smurf}</b>, ¿a quién corresponde?:</span>
                          ${for (option <- smurfSelection.options) yield {
            val (playerOption, checked, id, name) = option
            html"""<label><input type="radio" id="$id" name="$name" checked=$checked onchange=${(event: Event) =>
                {
                  uploadState.value = uploadState.value
                    .addSmurfToParticipant(id, smurfSelection.smurf)
                }}/>$playerOption</label>"""
          }}
                        </fieldset>
                      """
      }}
    </article>
    <article>
      <header>Resultado General (todas las partidas)</header>
      <form>
          ${
        val radioName = "match-result"

        def createRadio(value: String, label: String, isSelected: Boolean) = {
          val radio: Binding.Stable[Input] =
            html"""<input type="radio" name=$radioName value=$value checked=$isSelected />"""
          radio.value.onchange = (event: Event) => {
            if (radio.value.checked) {
              uploadState.value = uploadState.value.withWinner(value)
            }
          }
          html"""<label>$radio $label</label>"""
        }

        if (currentState.bind.countValidGames > 0) {
          if (currentState.bind.countValidGames % 2 == 0) {
            html"""<fieldset>
        ${createRadio(
                "FirstUser",
                s"Gana ${currentState.bind.firstParticipant.userName}",
                currentState.bind.winner == FirstUser
              )}
        ${createRadio(
                "SecondUser",
                s"Gana ${currentState.bind.secondParticipant.userName}",
                currentState.bind.winner == SecondUser
              )}
        ${createRadio("Draw", "Empate", currentState.bind.winner == Draw)}
      </fieldset>"""
          } else {
            html"""<fieldset>
        ${createRadio(
                "FirstUser",
                s"Gana ${currentState.bind.firstParticipant.userName}",
                currentState.bind.winner == FirstUser
              )}
        ${createRadio(
                "SecondUser",
                s"Gana ${currentState.bind.secondParticipant.userName}",
                currentState.bind.winner == SecondUser
              )}
      </fieldset>"""
          }
        } else {
          html"""<fieldset>
      ${createRadio(
              "Cancelled",
              "WO para los dos, cancelado",
              currentState.bind.winner == Cancelled
            )}
      ${createRadio(
              "FirstUserByOnlyPresented",
              s"Gana ${currentState.bind.firstParticipant.userName} (rival W.O.)",
              currentState.bind.winner == FirstUserByOnlyPresented
            )}
      ${createRadio(
              "SecondUserByOnlyPresented",
              s"Gana ${currentState.bind.secondParticipant.userName} (rival W.O.)",
              currentState.bind.winner == SecondUserByOnlyPresented
            )}
    </fieldset>"""
        }
      }
      <button type="button" disabled=${uploadState.bind.notEnoughToBeClosed} class="primary" onclick=${(_: Event) =>
        {
          val form =
            dom.document.getElementById("matchUploadForm").asInstanceOf[Form]
          // add list1 values
          uploadState.value.firstParticipant.smurfs.zipWithIndex.foreach {
            case (v, i) =>
              val input =
                dom.document.createElement("input").asInstanceOf[Input]
              input.`type` = "hidden"
              input.name = s"smurfsFirstParticipant[$i]"
              input.value = v
              form.appendChild(input)
          }

          // add list2 values
          uploadState.value.secondParticipant.smurfs.zipWithIndex.foreach {
            case (v, i) =>
              val input =
                dom.document.createElement("input").asInstanceOf[Input]
              input.`type` = "hidden"
              input.name = s"smurfsSecondParticipant[$i]"
              input.value = v
              form.appendChild(input)
          }

          // add single string
          val singleInput =
            dom.document.createElement("input").asInstanceOf[Input]
          singleInput.`type` = "hidden"
          singleInput.name = "winner"
          singleInput.value = uploadState.value.winner.toString
          form.appendChild(singleInput)
          form.submit()
        }}>
        <small>Enviar resultado global</small><br/>
        <span>${currentState.bind.getLabelStatus()}</span><br/>
        <small>Ya no podrás modificar nada</small>
      </button></form>
    </article>
    </div>
    </div>"""
  }
}
