package replayuploader

import org.scalajs.dom
import org.scalajs.dom.HTMLFormElement
import org.scalajs.dom.html.Input
import replayuploader.GenericReplayUploader

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
@JSExportTopLevel("Main")
object Main {
  def main(args: Array[String]): Unit = {}

  @JSExport("init")
  def init(tournamentID: String, challongeMatchID: String, containerID: String): Unit = {

    GenericReplayUploader.initTournament(challongeMatchID.toLong, tournamentID.toLong,containerID)

  }

  @JSExport("initCasualMatch")
  def initCasualMatch(casualMatchID: String, containerID: String): Unit = {

    GenericReplayUploader.initCasualMatch(casualMatchID.toLong, containerID)

  }

  def findTokenValue(): String = {
    val formElement =
      dom.document.getElementById("matchUploadForm").asInstanceOf[HTMLFormElement]
    val inputelementCollection = formElement.getElementsByTagName("input")
    val inputelement =
      inputelementCollection.namedItem("csrfToken").asInstanceOf[Input]
    inputelement.value
  }
}
