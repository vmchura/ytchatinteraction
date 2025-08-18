package replayuploader

import evolutioncomplete.WinnerShared.Draw
import evolutioncomplete.{ParticipantShared, UploadStateShared}
import evolutioncomplete.GameStateShared.*
import scala.concurrent.Future
import scala.util.{Success, Failure, Try}
import sttp.client4.*
import fetch.FetchBackend
import scala.concurrent.ExecutionContext.Implicits.global
import com.thoughtworks.binding.Binding, Binding.Var
import com.yang_bo.html.*
import upickle.default.*
import com.thoughtworks.binding.FutureBinding
import org.scalajs.dom.html.{Button, Div}
import com.thoughtworks.binding.Binding.Constants
import org.scalajs.dom.Event

object ReplayUploader {
  val uploadMatchState = Var[UploadStateShared](UploadStateShared.default())

  def init(tournamentID: Int, matchID: Int, containerID: String): Unit = {
    println(s"$tournamentID - $matchID - $containerID")
    val container = org.scalajs.dom.document.getElementById(containerID)
    render(container, uploadDivision(uploadMatchState))
    fetchState().onComplete {
      case Success(Right(value)) => uploadMatchState.value = value
      case Success(Left(error)) => uploadMatchState.value = UploadStateShared.errorOne()
      case Failure(error) => uploadMatchState.value = UploadStateShared.errorOne()
    }
  }

  def fetchState(): Future[Either[String, UploadStateShared]] = {

    val request = basicRequest
      // send the body as form data (x-www-form-urlencoded)
      .body(Map("name" -> "John", "surname" -> "doe"))
      .headers(Map("Csrf-Token" -> Main.findTokenValue()))
      // use an optional parameter in the URI
      .post(uri"/mydata")

    val backend = FetchBackend()
    request.send(backend).map {
      case Response(Right(jsonResponse), sttp.model.StatusCode.Ok, _, _, _, _) =>
        Try(read[UploadStateShared](jsonResponse)) match {
          case Success(valid) => Right(valid)
          case Failure(error) => Left(error.getLocalizedMessage)
        }
      case _ => Left("bad response")
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
        ${
            for (game <- currentState.bind.games) yield {
              html"""<div class="game-row">${game match {
                  case ValidGame(smurfs, mapName, playedAt, hash) =>
                    html"""<span class="status-icon success">✓</span>
                          <span class="player_left">${currentState.bind.getFirstSmurf(game)}</span>
                          <span class="vs">vs</span>
                          <span class="player_right">${currentState.bind.getSecondSmurf(game)}</span>
                          <button class="delete-button outline contrast error">&#x1F5D1;</button>"""
                  case PendingGame(_) =>
                    html"""<span class="status-icon pending">⌛</span>
                                  <span class="inner_space"><progress></progress></span>
                                  <span class="delete-button"></span>
                                  """
                  case InvalidGame(errorMessage) =>
                    html"""<span class="status-icon error">◯</span>
                        <span class="inner_space error-text">$errorMessage</span>

                                  <button class="delete-button outline contrast error">&#x1F5D1;</button>"""
                }
              }</div>"""
            }
        }

        <button type="button" class="outline add_more_replays" onclick="addMoreReplays()">
          + Add more replays
        </button>

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
        <h3>Match Result</h3>
        <select id="match-result" aria-label="Select match result">
          <option value="" selected>Select match result</option>
          <option value="player1-win">Player 1 Wins</option>
          <option value="player2-win">Player 2 Wins</option>
          <option value="draw">Draw</option>
        </select>
      </div>

      <button type="button" class="contrast" style="width: 100%; margin-top: 1rem;" onclick="submitMatch()">
        Submit Match Result
      </button>
    </div>"""
  }
}
