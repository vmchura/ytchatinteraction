package models

import evolutioncomplete.WinnerShared
import models.MatchStatus.Completed
import play.api.libs.json.*
// Import the Tournament JSON formatters
import models.TournamentModels._

/**
 * Represents a match from the Challonge API.
 */
case class ChallongeMatch(
  id: Long,
  state: String,
  player1Id: Option[Long],
  player2Id: Option[Long],
  winnerId: Option[Long],
  loserId: Option[Long],
  scheduledTime: Option[String],
  opponent: String,
  scores_csv: Option[String]
) {
  def matchStatus: models.MatchStatus = {
    state match {
      case "pending" => models.MatchStatus.Pending
      case "open" => models.MatchStatus.InProgress
      case "complete" => models.MatchStatus.Completed
      case _ => models.MatchStatus.Pending
    }
  }
  def winnerShared: WinnerShared = {
    matchStatus match {
      case Completed =>
        (scores_csv,player1Id.map(winnerId.contains), player2Id.map(winnerId.contains)) match {
          case (Some("1-0"), Some(true), Some(false)) => WinnerShared.FirstUser
          case (Some("0-0"), Some(true), Some(false)) => WinnerShared.FirstUserByOnlyPresented
          case (Some("0-1"), Some(false), Some(true)) => WinnerShared.SecondUser
          case (Some("0-0"), Some(false), Some(true)) => WinnerShared.SecondUserByOnlyPresented
          case (Some("1-1"), Some(false), Some(false)) => WinnerShared.Draw
          case (Some("0-0"), Some(false), Some(false)) => WinnerShared.Cancelled
          case _ => WinnerShared.Undefined
        }
      case _ => WinnerShared.Undefined
    }
  }
}

object ChallongeMatch {
  implicit val challengeMatchReads: Reads[ChallongeMatch] = Json.reads[ChallongeMatch]
  implicit val challengeMatchWrites: Writes[ChallongeMatch] = Json.writes[ChallongeMatch]
}

/**
 * Represents a participant from the Challonge API.
 */
case class ChallongeParticipant(
  id: Long,
  name: String
)

object ChallongeParticipant {
  implicit val challengeParticipantReads: Reads[ChallongeParticipant] = Json.reads[ChallongeParticipant]
  implicit val challengeParticipantWrites: Writes[ChallongeParticipant] = Json.writes[ChallongeParticipant]
}

/**
 * Case class to represent match information for the user dashboard.
 */
case class UserMatchInfo(
  tournament: Tournament,
  matchId: String,
  challengeMatchId: Long,
  opponent: String,
  status: String,
  scheduledTime: Option[String],
  winnerId: Option[Long]
)

object UserMatchInfo {
  implicit val userMatchInfoWrites: Writes[UserMatchInfo] = Json.writes[UserMatchInfo]
}
