package replayuploader

import org.scalajs.dom
import org.scalajs.dom.HTMLFormElement
import org.scalajs.dom.html.Input
import replayuploader.ReplayUploader

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
@JSExportTopLevel("Main")
object Main {
  def main(args: Array[String]): Unit = {}

  @JSExport("init")
  def init(tournamentID: Int, matchID: Int, containerID: String): Unit = {

    ReplayUploader.init(tournamentID,matchID,containerID)

  }

  def findTokenValue(): String = {
    val formElement =
      dom.document.getElementById("myForm").asInstanceOf[HTMLFormElement]
    val inputelementCollection = formElement.getElementsByTagName("input")
    val inputelement =
      inputelementCollection.namedItem("csrfToken").asInstanceOf[Input]
    inputelement.value
  }
}