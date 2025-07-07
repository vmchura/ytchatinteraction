package models

import java.util.UUID
import java.time.Instant

/**
 * Represents a match in a tournament between two users
 */
case class TournamentMatch(
  matchId: String,
  tournamentId: String,
  firstUserId: String,
  secondUserId: String,
  createdAt: Instant = Instant.now(),
  status: MatchStatus = MatchStatus.Pending
)

object TournamentMatch {
  def apply(tournamentId: String, firstUserId: String, secondUserId: String): TournamentMatch = {
    TournamentMatch(
      matchId = UUID.randomUUID().toString,
      tournamentId = tournamentId,
      firstUserId = firstUserId,
      secondUserId = secondUserId
    )
  }
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
}
