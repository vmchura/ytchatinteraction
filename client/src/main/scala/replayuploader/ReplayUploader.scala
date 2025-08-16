package replayuploader

object ReplayUploader {
  def init(tournamentID: Int, matchID: Int, containerID: String): Unit = {
    println(s"$tournamentID - $matchID - $containerID")
  }
}
