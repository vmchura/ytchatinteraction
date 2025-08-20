package evolutioncomplete
import evolutioncomplete.WinnerShared.Cancelled
import upickle.default.ReadWriter as RW
import upickle.default.*

import java.time.LocalDateTime
import GameStateShared.*

import java.util.UUID
case class ParticipantShared(userID: Long, userName: String, smurfs: Set[String]) derives ReadWriter
sealed trait GameStateShared {
  def sessionID: UUID
}
object GameStateShared {
  case class ValidGame(smurfs: List[String], mapName: String, playedAt: LocalDateTime, hash: String, sessionID: UUID) extends GameStateShared derives ReadWriter
  case class PendingGame(sessionID: UUID) extends GameStateShared derives ReadWriter
  case class InvalidGame(errorMessage: String, sessionID: UUID) extends GameStateShared derives ReadWriter
  given ReadWriter[LocalDateTime] =
    readwriter[String].bimap[LocalDateTime](
      dt => dt.toString, // default ISO-8601
      str => LocalDateTime.parse(str)
    )
  given RW[GameStateShared] = macroRW
}
enum WinnerShared derives ReadWriter:
  case Undefined, FirstUser, SecondUser, Draw, FirstUserByOnlyPresented, SecondUserByOnlyPresented, Cancelled

case class SmurfSelection(smurf: String, options: List[(String, Boolean, String, String)])

case class UploadStateShared(challongeMatchID: Long, tournamentID: Long,
                             firstParticipant: ParticipantShared, secondParticipant: ParticipantShared,
                             games: List[GameStateShared], winner: WinnerShared) derives ReadWriter {
  def getGameDescription(game: GameStateShared): (String, String) = {
    game match {
      case ValidGame(smurf1 :: smurf2 :: _, _, _, _, _) =>
        (firstParticipant.smurfs.contains(smurf1),firstParticipant.smurfs.contains(smurf2),
          secondParticipant.smurfs.contains(smurf1),secondParticipant.smurfs.contains(smurf2)) match {
          case (true, false, false, true) => (s"$smurf1",s"$smurf2")
          case (true, false, false, false) => (s"$smurf1",s"[$smurf2]?")
          case (false, true, true, false) => (s"$smurf2",s"$smurf1")
          case (false, true, false, false) => (s"$smurf2",s"[$smurf1]?")
          case (false, false, true, false) => (s"[$smurf2]?",s"$smurf1")
          case (false, false, false, true) => (s"[$smurf1]?",s"$smurf2")
          case (_, _, _, _) => (s"[$smurf1]?",s"[$smurf2]?")
        }
      case _ => throw new IllegalStateException("Description of invalid")
    }
  }
  def getFirstSmurf(game: GameStateShared): String = getGameDescription(game)._1
  def getSecondSmurf(game: GameStateShared): String = getGameDescription(game)._2
  def addSmurfToFirstParticipant(smurf: String): UploadStateShared = {
    copy(firstParticipant = firstParticipant.copy(smurfs = firstParticipant.smurfs + smurf),
      secondParticipant = secondParticipant.copy(smurfs = secondParticipant.smurfs - smurf))
  }

  def addSmurfToSecondParticipant(smurf: String): UploadStateShared = {
    copy(firstParticipant = firstParticipant.copy(smurfs = firstParticipant.smurfs - smurf),
      secondParticipant = secondParticipant.copy(smurfs = secondParticipant.smurfs + smurf))
  }
  def addSmurfToParticipant(id: String, smurf: String): UploadStateShared = {
    val allSmurfs = getSmurfs.map(_._1)
    if(allSmurfs.contains(smurf)){
      if(allSmurfs.length == 2){
        val otherSmurf = allSmurfs.filterNot(_.equals(smurf)).head
        id.split("_").tail.map(_.toInt) match {
          case Array(1) => addSmurfToFirstParticipant(smurf).addSmurfToSecondParticipant(otherSmurf)
          case Array(2) => addSmurfToSecondParticipant(smurf).addSmurfToFirstParticipant(otherSmurf)
          case _ => this
        }
      }else{
        id.split("_").tail.map(_.toInt) match {
          case Array(1) => addSmurfToFirstParticipant(smurf)
          case Array(2) => addSmurfToSecondParticipant(smurf)
          case _ => this
        }
      }

    }else{
      this
    }

  }
  def getSmurfs: List[SmurfSelection] = {
    val allSmurfs = games.flatMap{
      case ValidGame(smurfs, _, _, _, _) => smurfs
      case _ => Nil
    }.distinct
    allSmurfs.zipWithIndex.map{ case (singleSmurf, i) =>
      SmurfSelection(singleSmurf, List((firstParticipant.userName, firstParticipant.smurfs.contains(singleSmurf), s"${i}_1", s"${i}_smurf"),
        (secondParticipant.userName, secondParticipant.smurfs.contains(singleSmurf), s"${i}_2", s"${i}_smurf")))
    }

  }
  def withWinner(winnerShared: String): UploadStateShared = {
    copy(winner=WinnerShared.valueOf(winnerShared))
  }

  def withGames(newGames: List[GameStateShared]): UploadStateShared = {
    copy(games = games ::: newGames)
  }
  def updateOnePendingTo(f: UUID => GameStateShared): UploadStateShared = {
    val (noPending, firstPends) = games.span{
      case PendingGame(_) => false
      case _ => true
    }
    firstPends match {
      case PendingGame(uuid) :: rest => copy(games=  f(uuid) ::  rest :::  noPending)
      case _ => throw new IllegalStateException("No pending?")
    }
  }
  def updateToPending(sessionUUID: UUID): UploadStateShared = {
    val newGames = games.map{
      case g if g.sessionID.compareTo(sessionUUID) == 0 => PendingGame(g.sessionID)
      case g => g
    }
    copy(games = newGames)
  }
}
object UploadStateShared {
  def default(): UploadStateShared = UploadStateShared(0,0, ParticipantShared(0, "Flash", Set.empty[String]), ParticipantShared(0, "Bisu", Set.empty[String]), Nil, Cancelled)
  def errorOne(): UploadStateShared = UploadStateShared(0,0, ParticipantShared(0, "Error", Set.empty[String]), ParticipantShared(0, "Error", Set.empty[String]), Nil, Cancelled)
}
