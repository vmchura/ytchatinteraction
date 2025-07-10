package models

import java.util.UUID
import java.time.Instant
import play.api.libs.json.*

/**
 * Represents a match in a tournament between two users
 */
case class TournamentMatch(
  matchId: Long,
  tournamentId: Long,
  firstUserId: Long,
  secondUserId: Long,
  createdAt: Instant = Instant.now(),
  status: MatchStatus = MatchStatus.Pending
)

object TournamentMatch {
  implicit val tournamentMatchFormat: Format[TournamentMatch] = Json.format[TournamentMatch]
}

/**
 * Status of a tournament match
 */
sealed trait MatchStatus

object MatchStatus {
  case object Pending extends MatchStatus
  case object InProgress extends MatchStatus
  case object Completed extends MatchStatus
  case object Disputed extends MatchStatus
  case object Cancelled extends MatchStatus

  implicit val matchStatusFormat: Format[MatchStatus] = new Format[MatchStatus] {
    def reads(json: JsValue): JsResult[MatchStatus] = {
      json.validate[String].map {
        case "Pending" => Pending
        case "InProgress" => InProgress
        case "Completed" => Completed
        case "Disputed" => Disputed
        case "Cancelled" => Cancelled
        case other => throw new IllegalArgumentException(s"Invalid MatchStatus: $other")
      }
    }

    def writes(status: MatchStatus): JsValue = {
      JsString(status.toString)
    }
  }
}
