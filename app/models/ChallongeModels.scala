package models

import play.api.libs.json._
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
  opponent: String // This will be populated based on the requesting user
)

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
