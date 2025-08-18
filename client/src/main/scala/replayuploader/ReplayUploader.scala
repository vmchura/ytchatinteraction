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
      <h1 style="text-align: center; margin-bottom: 2rem;">${currentState.bind.firstParticipant.userName} vs ${currentState.bind.secondParticipant.userName}</h1>

      <div class="games-list">
        ${
          for (game <- currentState.bind.games) yield {
            html"""<div class="game-item">
                ${game match {
                  case ValidGame(smurfs, mapName, playedAt, hash) =>
                    html"""<span class="status-icon success">✓</span>
                  <span class="game-info">${currentState.bind.getGameDescription(game)}</span>"""
                  case PendingGame(_) =>
                    html"""<span class="status-icon pending">&#x231B;</span>
                          <span class="game-info"><progress /></span>"""
                  case InvalidGame(errorMessage) =>
                    html"""<span class="status-icon error">◯</span>
                  <span class="game-info error-text">$errorMessage</span>"""
                  }
                }
            </div>"""
          }
        }

          <button type="button" class="outline" style="width: 100%; margin-top: 1rem;" onclick="addMoreReplays()">
            + Add more replays
          </button>
      </div>
      <div class="smurf_selection">
          ${
            Constants(currentState.bind.getSmurfs*).flatMap{ smurfSelection =>
              html"""<fieldset>
                          <legend>${smurfSelection.smurf}</legend>
                          ${
                Constants(smurfSelection.options*).flatMap{option =>
                  val (playerOption, checked, id, name) = option
                  html"""<input type="radio" id="$id" name="$name" checked=$checked onchange=${(event: Event) => {uploadMatchState.value = uploadMatchState.value.addSmurfToParticipant(id, smurfSelection.smurf)}}/>
                  <label htmlFor="$id">$playerOption</label>"""
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
