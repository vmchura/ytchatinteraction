package evolutioncomplete
import evolutioncomplete.WinnerShared.Cancelled
import upickle.default.ReadWriter as RW
import upickle.default.*

import java.time.LocalDateTime

case class ParticipantShared(userID: Int, userName: String, smurfs: List[String]) derives ReadWriter
sealed trait GameStateShared
object GameStateShared {
  case class ValidGame(smurfs: List[String], mapName: String, playedAt: LocalDateTime, hash: String) extends GameStateShared derives ReadWriter
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


case class UploadStateShared(matchID: Int, tournamentID: Int,
                             firstParticipant: ParticipantShared, secondParticipant: ParticipantShared,
                             games: List[GameStateShared], winner: WinnerShared) derives ReadWriter
object UploadStateShared {
  def default(): UploadStateShared = UploadStateShared(0,0, ParticipantShared(0, "Flash", Nil), ParticipantShared(0, "Bisu", Nil), Nil, Cancelled)
  def errorOne(): UploadStateShared = UploadStateShared(0,0, ParticipantShared(0, "Error", Nil), ParticipantShared(0, "Error", Nil), Nil, Cancelled)
}
