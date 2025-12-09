package evolutioncomplete

import evolutioncomplete.WinnerShared._
import upickle.default.ReadWriter as RW
import upickle.default.*

import java.time.LocalDateTime
import GameStateShared.*

import java.util.UUID

case class ParticipantShared(
    userID: Long,
    userName: String,
    smurfs: Set[String]
) derives ReadWriter

sealed trait GameStateShared {
  def sessionID: UUID
}

object GameStateShared {
  case class ValidGame(
      smurfs: List[String],
      mapName: String,
      playedAt: LocalDateTime,
      hash: String,
      sessionID: UUID
  ) extends GameStateShared derives ReadWriter

  case class PendingGame(sessionID: UUID) extends GameStateShared
      derives ReadWriter

  case class InvalidGame(errorMessage: String, sessionID: UUID)
      extends GameStateShared derives ReadWriter

  given ReadWriter[LocalDateTime] =
    readwriter[String].bimap[LocalDateTime](
      dt => dt.toString, // default ISO-8601
      str => LocalDateTime.parse(str)
    )

  given RW[GameStateShared] = macroRW
}

enum WinnerShared derives ReadWriter:
  case Undefined, FirstUser, SecondUser, Draw, FirstUserByOnlyPresented,
    SecondUserByOnlyPresented, Cancelled

case class SmurfSelection(
    smurf: String,
    options: List[(String, Boolean, String, String)]
)

trait TUploadStateShared[SS <: TUploadStateShared[SS]] { this: SS =>
  def firstParticipant: ParticipantShared
  def secondParticipant: ParticipantShared
  def games: List[GameStateShared]
  def winner: WinnerShared
  def withFirstParticipant(participant: ParticipantShared): SS
  def withSecondParticipant(participant: ParticipantShared): SS
  def withGames(games: List[GameStateShared]): SS
  def withWinner(winner: WinnerShared): SS

  def getLabelStatus(): String = {
    winner match {
      case Undefined                            => "-"
      case FirstUser | FirstUserByOnlyPresented =>
        f"Gana ${firstParticipant.userName}"
      case SecondUser | SecondUserByOnlyPresented =>
        f"Gana ${secondParticipant.userName}"
      case Draw      => "Empate"
      case Cancelled => "Cancelado"
    }
  }

  def getGameDescription(game: GameStateShared): (String, String) = {
    game match {
      case ValidGame(smurf1 :: smurf2 :: _, _, _, _, _) =>
        (
          firstParticipant.smurfs.contains(smurf1),
          firstParticipant.smurfs.contains(smurf2),
          secondParticipant.smurfs.contains(smurf1),
          secondParticipant.smurfs.contains(smurf2)
        ) match {
          case (true, false, false, true)  => (s"$smurf1", s"$smurf2")
          case (true, false, false, false) => (s"$smurf1", s"[$smurf2]?")
          case (false, true, true, false)  => (s"$smurf2", s"$smurf1")
          case (false, true, false, false) => (s"$smurf2", s"[$smurf1]?")
          case (false, false, true, false) => (s"[$smurf2]?", s"$smurf1")
          case (false, false, false, true) => (s"[$smurf1]?", s"$smurf2")
          case (_, _, _, _)                => (s"[$smurf1]?", s"[$smurf2]?")
        }
      case _ => throw new IllegalStateException("Description of invalid")
    }
  }

  def getFirstSmurf(game: GameStateShared): String = getGameDescription(game)._1

  def getSecondSmurf(game: GameStateShared): String = getGameDescription(
    game
  )._2

  def addSmurfToFirstParticipant(smurf: String): SS = {
    withFirstParticipant(
      firstParticipant.copy(smurfs = firstParticipant.smurfs + smurf)
    ).withSecondParticipant(
      secondParticipant.copy(smurfs = secondParticipant.smurfs - smurf)
    )
  }

  def addSmurfToSecondParticipant(smurf: String): SS = {
    withFirstParticipant(
      firstParticipant.copy(smurfs = firstParticipant.smurfs - smurf)
    ).withSecondParticipant(
      secondParticipant.copy(smurfs = secondParticipant.smurfs + smurf)
    )
  }

  def addSmurfToParticipant(id: String, smurf: String): SS = {
    val allSmurfs = getSmurfs.map(_._1)
    if (allSmurfs.contains(smurf)) {
      if (allSmurfs.length == 2) {
        val otherSmurf = allSmurfs.filterNot(_.equals(smurf)).head
        id.split("_").tail.map(_.toInt) match {
          case Array(1) =>
            addSmurfToFirstParticipant(smurf).addSmurfToSecondParticipant(
              otherSmurf
            )
          case Array(2) =>
            addSmurfToSecondParticipant(smurf).addSmurfToFirstParticipant(
              otherSmurf
            )
          case _ => this
        }
      } else {
        id.split("_").tail.map(_.toInt) match {
          case Array(1) => addSmurfToFirstParticipant(smurf)
          case Array(2) => addSmurfToSecondParticipant(smurf)
          case _        => this
        }
      }

    } else {
      this
    }

  }

  def getSmurfs: List[SmurfSelection] = {
    val allSmurfs = games.flatMap {
      case ValidGame(smurfs, _, _, _, _) => smurfs
      case _                             => Nil
    }.distinct
    allSmurfs.zipWithIndex.map { case (singleSmurf, i) =>
      SmurfSelection(
        singleSmurf,
        List(
          (
            firstParticipant.userName,
            firstParticipant.smurfs.contains(singleSmurf),
            s"${i}_1",
            s"${i}_smurf"
          ),
          (
            secondParticipant.userName,
            secondParticipant.smurfs.contains(singleSmurf),
            s"${i}_2",
            s"${i}_smurf"
          )
        )
      )
    }

  }

  def withWinner(winnerShared: String): SS = {
    withWinner(WinnerShared.valueOf(winnerShared))
  }

  def calculateWinner(): SS = {
    if (games.isEmpty) {
      winner match {
        case FirstUserByOnlyPresented | SecondUserByOnlyPresented => this
        case _ => withWinner(Cancelled)
      }
    } else {
      winner match {
        case FirstUser | SecondUser => this
        case Draw                   =>
          if (games.length % 2 == 0)
            this
          else withWinner(FirstUser)
        case _ => withWinner(FirstUser)
      }
    }
  }

  def withExtraGames(newGames: List[GameStateShared]): SS = {
    withGames(games ::: newGames)
  }

  def updateOnePendingTo(f: UUID => GameStateShared): SS = {
    val (noPending, firstPends) = games.span {
      case PendingGame(_) => false
      case _              => true
    }
    firstPends match {
      case PendingGame(uuid) :: rest =>
        withGames(f(uuid) :: rest ::: noPending)
      case _ => throw new IllegalStateException("No pending?")
    }
  }

  def updateToPending(sessionUUID: UUID): SS = {
    val newGames = games.map {
      case g if g.sessionID.compareTo(sessionUUID) == 0 =>
        PendingGame(g.sessionID)
      case g => g
    }
    withGames(newGames)
  }

  def notEnoughToBeClosed: Boolean = {
    (winner == Undefined) || (if (
                                games.count {
                                  case _: ValidGame => true
                                  case _            => false
                                } > 0
                              ) {
                                (firstParticipant.smurfs ++ secondParticipant.smurfs).isEmpty
                              } else false) || games.exists {
      case _: PendingGame => true
      case _              => false
    }
  }

  def countValidGames: Int = {
    games.count {
      case _: ValidGame => true
      case _            => false
    }
  }
}
case class TournamentUploadStateShared(
    challongeMatchID: Long,
    tournamentID: Long,
    firstParticipant: ParticipantShared,
    secondParticipant: ParticipantShared,
    games: List[GameStateShared],
    winner: WinnerShared
) extends TUploadStateShared[TournamentUploadStateShared] derives ReadWriter:
  override def withFirstParticipant(
      participant: ParticipantShared
  ): TournamentUploadStateShared = copy(firstParticipant = participant)
  override def withSecondParticipant(
      participant: ParticipantShared
  ): TournamentUploadStateShared = copy(secondParticipant = participant)
  override def withGames(
      games: List[GameStateShared]
  ): TournamentUploadStateShared = copy(games = games)
  override def withWinner(winner: WinnerShared): TournamentUploadStateShared =
    copy(winner = winner)

case class AnalyticalUploadStateShared(
    firstParticipant: ParticipantShared,
    games: List[GameStateShared]
) extends TUploadStateShared[AnalyticalUploadStateShared] derives ReadWriter:

  override def secondParticipant: ParticipantShared =
    throw new IllegalAccessException("no implemented secondParticipant")

  override def winner: WinnerShared = throw new IllegalAccessException(
    "no implemented winner"
  )
  override def withFirstParticipant(
      participant: ParticipantShared
  ): AnalyticalUploadStateShared = copy(firstParticipant = participant)
  override def withSecondParticipant(
      participant: ParticipantShared
  ): AnalyticalUploadStateShared = throw new IllegalAccessException(
    "no implemented withSecondParticipant"
  )
  override def withGames(
      games: List[GameStateShared]
  ): AnalyticalUploadStateShared = copy(games = games)
  override def withWinner(winner: WinnerShared): AnalyticalUploadStateShared =
    throw new IllegalAccessException("no implemented withWinner")

object TournamentUploadStateShared {
  def default(): TournamentUploadStateShared = TournamentUploadStateShared(
    0,
    0,
    ParticipantShared(0, "Flash", Set.empty[String]),
    ParticipantShared(0, "Bisu", Set.empty[String]),
    Nil,
    Cancelled
  )

  def errorOne(): TournamentUploadStateShared = TournamentUploadStateShared(
    0,
    0,
    ParticipantShared(0, "Error", Set.empty[String]),
    ParticipantShared(0, "Error", Set.empty[String]),
    Nil,
    Cancelled
  )
}
case class PotentialAnalyticalFileShared(
    uploadedFile: Long,
    userSlotId: Int,
    userRace: String,
    rivalRace: String,
    frames: Int,
    userId: Long
)
