package replayuploader

import evolutioncomplete.WinnerShared.*
import evolutioncomplete.{GameStateShared, ParticipantShared, UploadStateShared}
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

object ReplayUploader {
  val uploadMatchState = Var[UploadStateShared](UploadStateShared.default())

  def init(tournamentID: Int, challongeMatchID: Int, containerID: String): Unit = {
    val container = org.scalajs.dom.document.getElementById(containerID)
    render(container, uploadDivision(uploadMatchState))
    fetchState(tournamentID: Int, challongeMatchID: Int).onComplete {
      case Success(Right(value)) => uploadMatchState.value = value
      case Success(Left(error)) => uploadMatchState.value =
        UploadStateShared.errorOne()
      case Failure(error) =>
        uploadMatchState.value = UploadStateShared.errorOne()
    }
  }

  def fetchState(tournamentID: Int, challongeMatchID: Int): Future[Either[String, UploadStateShared]] = {

    val request = basicRequest
      // send the body as form data (x-www-form-urlencoded)
      .headers(Map("Csrf-Token" -> Main.findTokenValue()))
      // use an optional parameter in the URI
      .get(uri"/fetchstate/$challongeMatchID/$tournamentID")

    val backend = FetchBackend()
    request.send(backend).map {
      case Response(Right(jsonResponse), sttp.model.StatusCode.Ok, _, _, _, _) =>
        Try(read[UploadStateShared](jsonResponse)) match {
          case Success(valid) => Right(valid)
          case Failure(error) => Left(error.getLocalizedMessage)
        }
      case error => Left(error.toString)
    }
  }

  def postState(validFiles: List[File], uploadMatchState: UploadStateShared): Future[Either[String, UploadStateShared]] = {

    // Create file parts
    val fileParts = validFiles.map { file =>
      multipartFile("replays", file).fileName(file.name)
    }

    // Create JSON state part
    val statePart = multipart("state", write(uploadMatchState)).contentType("application/json")

    // Combine all parts
    val allParts = fileParts :+ statePart

    val request = basicRequest
      .multipartBody(allParts)
      .headers(Map("Csrf-Token" -> Main.findTokenValue()))
      .post(uri"/updatestate")

    val backend = FetchBackend()
    request.send(backend).map {
      case Response(Right(jsonResponse), sttp.model.StatusCode.Ok, _, _, _, _) =>
        Try(read[UploadStateShared](jsonResponse)) match {
          case Success(valid) => Right(valid)
          case Failure(error) => Left(error.getLocalizedMessage)
        }
      case error => Left(error.toString)
    }
  }

  def removeFile(uuid: UUID): Unit = {
    uploadMatchState.value.updateToPending(uuid)
    val request = basicRequest
      .headers(Map("Csrf-Token" -> Main.findTokenValue()))
      .post(uri"/remove/${uploadMatchState.value.tournamentID}/${uploadMatchState.value.challongeMatchID}/${uuid.toString}")
    val backend = FetchBackend()
    val response = request.send(backend).map {
      case Response(Right(jsonResponse), sttp.model.StatusCode.Ok, _, _, _, _) =>
        Try(read[UploadStateShared](jsonResponse)) match {
          case Success(valid) => Right(valid)
          case Failure(error) => Left(error.getLocalizedMessage)
        }
      case error => Left(error.toString)
    }
    response.onComplete {
      case Success(Right(value)) => uploadMatchState.value = value
      case Success(Left(error)) => println(error)
      case Failure(error) => println(error)
    }
  }

  def uploadDivision(currentState: Binding[UploadStateShared]): Binding[Div] = {
    html"""<div class="container" id="match_result">
         <div class="games-list">
           <h1 class="game-row">
           <span class="player_left">${currentState.bind.firstParticipant.userName}</span>
           <span class="vs">vs</span>
           <span class="player_right">${currentState.bind.secondParticipant.userName}</span>
         </h1>
         <h6>Replays</h6>
        ${
      for (game <- currentState.bind.games) yield {
        html"""<div class="game-row">${
          game match {
            case ValidGame(smurfs, mapName, playedAt, hash, uuid) =>
              html"""<span class="status-icon success">✓</span>
                          <span class="player_left">${currentState.bind.getFirstSmurf(game)}</span>
                          <span class="vs">vs</span>
                          <span class="player_right">${currentState.bind.getSecondSmurf(game)}</span>
                          <button class="delete-button outline contrast error" onclick=${ (_: Event) => removeFile(uuid) }>&#x1F5D1;</button>"""
            case PendingGame(_) =>
              html"""<span class="status-icon pending">⌛</span>
                                  <span class="inner_space"><progress></progress></span>
                                  <span class="delete-button"></span>
                                  """
            case InvalidGame(errorMessage, uuid) =>
              html"""<span class="status-icon error">◯</span>
                        <span class="inner_space error-text">$errorMessage</span>
                        <button class="delete-button outline contrast error" onclick=${ (_: Event) => removeFile(uuid) }>&#x1F5D1;</button>"""
          }
        }</div>"""
      }
    }
        <label for="file-input" class="outline add_more_replays" role="button">
          ${
      val input: Binding.Stable[Input] = html"""<input type="file" id="file-input" hidden multiple>"""
      input.value.onchange = (_: Event) => {
        val (validFiles, invalidFiles) = input.value.files.toList.partition(f => f.size <= 1024 * 1024 && f.name.endsWith(".rep"))
        val invalidFilesReason = invalidFiles.map { f => if (f.size > 1024 * 1024) InvalidGame("Archivo > 1Mb", UUID.randomUUID()) else InvalidGame("Archivo no es *.rep", UUID.randomUUID()) }


        val pendingFiles = validFiles.map(_ => PendingGame(UUID.randomUUID()))
        uploadMatchState.value = uploadMatchState.value.withGames(pendingFiles ::: invalidFilesReason)
        postState(validFiles, uploadMatchState.value).onComplete {
          case Success(Right(value)) => uploadMatchState.value = value
          case Success(Left(error)) => println(error)
          case Failure(error) => println(error)
        }
      }
      input
    }
          + Agregar replays
        </label>
      <h6>Smurfs</h6>
          ${
      Constants(currentState.bind.getSmurfs *).flatMap { smurfSelection =>
        html"""<fieldset class="game-row">
                          <span class="status-icon">${smurfSelection.smurf}</span>
                          ${
          for (option <- smurfSelection.options) yield {
            val (playerOption, checked, id, name) = option
            html"""<div class=${
              if (id.endsWith("1")) {
                "player_left"
              } else {
                "player_right"
              }
            }><input type="radio" id="$id" name="$name" checked=$checked onchange=${ (event: Event) => {
              uploadMatchState.value = uploadMatchState.value.addSmurfToParticipant(id, smurfSelection.smurf)
            }
            }/>
                  <label htmlFor="$id">$playerOption</label></div>"""
          }

        }
                        </fieldset>
                      """
      }
    }
    </div>
      <div class="form-section">
        <h6>Resultado General (todas las partidas)</h6>
        ${
      val select_winner: Binding.Stable[Select] =
        html"""<select id="match-result">
          <option value="Undefined" selected=${currentState.bind.winner == Undefined}>Seleccionar</option>
          ${if(currentState.bind.countValidGames > 0) {html"""<option value="FirstUser" selected=${currentState.bind.winner == FirstUser}>Gana ${currentState.bind.firstParticipant.userName}</option>"""} else Binding.Constants()}
          ${if(currentState.bind.countValidGames > 0) {html"""<option value="SecondUser" selected=${currentState.bind.winner == SecondUser}>Gana ${currentState.bind.secondParticipant.userName}</option>"""} else Binding.Constants()}
          ${if(currentState.bind.countValidGames > 0 && (currentState.bind.countValidGames % 2 == 0)) {html"""<option value="Draw" selected=${currentState.bind.winner == Draw}>Empate</option>"""} else Binding.Constants()}
          ${if(currentState.bind.countValidGames == 0) {html"""<option value="FirstUserByOnlyPresented" selected=${currentState.bind.winner == FirstUserByOnlyPresented}>Gana ${currentState.bind.firstParticipant.userName} (rival W.O.)</option>"""} else Binding.Constants()}
          ${if(currentState.bind.countValidGames == 0) {html"""<option value="SecondUserByOnlyPresented" selected=${currentState.bind.winner == SecondUserByOnlyPresented}>Gana ${currentState.bind.secondParticipant.userName} (rival W.O.)</option>"""} else Binding.Constants()}
          ${if(currentState.bind.countValidGames == 0) {html"""<option value="Cancelled" selected=${currentState.bind.winner == Cancelled}>WO para los dos, cancelado</option>"""} else Binding.Constants()}
        </select>"""
      select_winner.value.onchange = (event: Event) => {
        uploadMatchState.value = uploadMatchState.value.withWinner(select_winner.value.value)
      }
      select_winner
    }
      </div>

      <button type="button" disabled=${uploadMatchState.bind.notEnoughToBeClosed} class="primary" onclick=${ (_ : Event) =>
      {
        val form =  dom.document.getElementById("matchUploadForm").asInstanceOf[Form]
        // add list1 values
        uploadMatchState.value.firstParticipant.smurfs.zipWithIndex.foreach { case (v, i) =>
          val input = dom.document.createElement("input").asInstanceOf[Input]
          input.`type` = "hidden"
          input.name = s"smurfsFirstParticipant[$i]"
          input.value = v
          form.appendChild(input)
        }

        // add list2 values
        uploadMatchState.value.secondParticipant.smurfs.zipWithIndex.foreach { case (v, i) =>
          val input = dom.document.createElement("input").asInstanceOf[Input]
          input.`type` = "hidden"
          input.name = s"smurfsSecondParticipant[$i]"
          input.value = v
          form.appendChild(input)
        }

        // add single string
        val singleInput = dom.document.createElement("input").asInstanceOf[Input]
        singleInput.`type` = "hidden"
        singleInput.name = "winner"
        singleInput.value = uploadMatchState.value.winner.toString
        form.appendChild(singleInput)
        form.submit()
      }
    }>
        <span>Enviar resultado global</span><br/><small>Ya no podrás modificar replays, smurfs ni resultado</small>
      </button>
    </div>"""
  }
}
