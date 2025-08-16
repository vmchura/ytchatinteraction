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
  winnerUserId: Option[Long],
  createdAt: Instant = Instant.now(),
  status: MatchStatus = MatchStatus.Pending
)


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
}
