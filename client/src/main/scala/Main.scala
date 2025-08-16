import replayuploader.ReplayUploader

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
@JSExportTopLevel("Main")
object Main {
  def main(args: Array[String]): Unit = {}

  @JSExport("init")
  def init(tournamentID: Int, matchID: Int, containerID: String): Unit = {

    ReplayUploader.init(tournamentID,matchID,containerID)

  }
}