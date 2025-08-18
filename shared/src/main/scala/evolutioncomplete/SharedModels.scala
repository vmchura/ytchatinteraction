package evolutioncomplete
import evolutioncomplete.WinnerShared.Cancelled
import upickle.default.ReadWriter as RW
import upickle.default.*

import java.time.LocalDateTime
import GameStateShared.*
case class ParticipantShared(userID: Int, userName: String, smurfs: Set[String]) derives ReadWriter
sealed trait GameStateShared
object GameStateShared {
  case class ValidGame(smurfs: List[String], mapName: String, playedAt: LocalDateTime, hash: String) extends GameStateShared derives ReadWriter
  case class PendingGame(id: Int) extends GameStateShared derives ReadWriter
  case class InvalidGame(errorMessage: String) extends GameStateShared derives ReadWriter
  given ReadWriter[LocalDateTime] =
    readwriter[String].bimap[LocalDateTime](
      dt => dt.toString, // default ISO-8601
      str => LocalDateTime.parse(str)
    )
  given RW[GameStateShared] = macroRW
}
case class GameFile(sessionID: Int, state: GameStateShared) derives ReadWriter
enum WinnerShared derives ReadWriter:
  case Undefined, FirstUser, SecondUser, Draw, FirstUserByOnlyPresented, SecondUserByOnlyPresented, Cancelled

case class SmurfSelection(smurf: String, options: List[(String, Boolean, String, String)])

case class UploadStateShared(matchID: Int, tournamentID: Int,
                             firstParticipant: ParticipantShared, secondParticipant: ParticipantShared,
                             games: List[GameStateShared], winner: WinnerShared) derives ReadWriter {
  def getGameDescription(game: GameStateShared): String = {
    game match {
      case ValidGame(smurf1 :: smurf2 :: _, _, _, _) =>
        (firstParticipant.smurfs.contains(smurf1),firstParticipant.smurfs.contains(smurf2),
          secondParticipant.smurfs.contains(smurf1),secondParticipant.smurfs.contains(smurf2)) match {
          case (true, false, false, true) => s"$smurf1 vs $smurf2"
          case (true, false, false, false) => s"$smurf1 vs [$smurf2]?"
          case (false, true, true, false) => s"$smurf2 vs $smurf1"
          case (false, true, false, false) => s"$smurf2 vs [$smurf1]?"
          case (false, false, true, false) => s"[$smurf2]? vs $smurf1"
          case (false, false, false, true) => s"[$smurf1]? vs $smurf2"
          case (_, _, _, _) => s"[$smurf1]? vs [$smurf2]?"
        }
      case _ => throw new IllegalStateException("Description of invalid")
    }
  }
  def addSmurfToFirstParticipant(smurf: String): UploadStateShared = {
    copy(firstParticipant = firstParticipant.copy(smurfs = firstParticipant.smurfs + smurf),
      secondParticipant = secondParticipant.copy(smurfs = secondParticipant.smurfs - smurf))
  }

  def addSmurfToSecondParticipant(smurf: String): UploadStateShared = {
    copy(firstParticipant = firstParticipant.copy(smurfs = firstParticipant.smurfs - smurf),
      secondParticipant = secondParticipant.copy(smurfs = secondParticipant.smurfs + smurf))
  }
  def addSmurfToParticipant(id: String, smurf: String): UploadStateShared = {
    id.split("_").tail.map(_.toInt) match {
      case Array(1) => addSmurfToFirstParticipant(smurf)
      case Array(2) => addSmurfToSecondParticipant(smurf)
      case _ => this
    }
  }
  def getSmurfs: List[SmurfSelection] = {
    val allSmurfs = games.flatMap{
      case ValidGame(smurfs, _, _, _) => smurfs
      case _ => Nil
    }.distinct
    allSmurfs.zipWithIndex.map{ case (singleSmurf, i) =>
      SmurfSelection(singleSmurf, List((firstParticipant.userName, firstParticipant.smurfs.contains(singleSmurf), s"${i}_1", s"${i}_smurf"),
        (secondParticipant.userName, secondParticipant.smurfs.contains(singleSmurf), s"${i}_2", s"${i}_smurf")))
    }

  }
}
object UploadStateShared {
  def default(): UploadStateShared = UploadStateShared(0,0, ParticipantShared(0, "Flash", Set.empty[String]), ParticipantShared(0, "Bisu", Set.empty[String]), Nil, Cancelled)
  def errorOne(): UploadStateShared = UploadStateShared(0,0, ParticipantShared(0, "Error", Set.empty[String]), ParticipantShared(0, "Error", Set.empty[String]), Nil, Cancelled)
}
