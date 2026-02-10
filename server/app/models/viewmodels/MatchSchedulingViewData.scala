package models.viewmodels

import services.PotentialMatchTime

/**
 * View data for match scheduling page.
 * Wraps PotentialMatchTime with display context for both players.
 */
case class MatchSchedulingViewData(
    tournamentId: Long,
    matchId: Long,
    player1: PlayerSchedulingInfo,
    player2: PlayerSchedulingInfo,
    suggestedTimes: List[PotentialMatchTime]
)

case class PlayerSchedulingInfo(
    userId: Long,
    userName: String,
    timezone: String,
    hasAvailability: Boolean
)