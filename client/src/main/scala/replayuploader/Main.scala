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
  def init(tournamentID: Int, challongeMatchID: Int, containerID: String): Unit = {

    GenericReplayUploader.initTournament(challongeMatchID, tournamentID,containerID)

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
