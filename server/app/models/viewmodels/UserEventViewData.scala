package models.viewmodels

import models.*

case class UserEventViewData(
  userEvents: UserEventsData,
  tournaments: TournamentViewData,
  matches: List[UserMatchInfo],
  webSocketUrl: String,
  user: User
)

case class UserEventsData(
  activeEvents: Seq[FrontalStreamerEvent],
  channelBalances: Map[String, Int],
  availableEvents: Seq[FrontalStreamerEvent]
) {
  def hasActiveEvents: Boolean = activeEvents.nonEmpty
  def hasAvailableEvents: Boolean = availableEvents.nonEmpty
  def getBalanceForChannel(channelId: String): Int = channelBalances.getOrElse(channelId, 0)
}

case class TournamentViewData(
  openTournaments: List[Tournament],
  registrationStatus: Map[Tournament, Boolean]
) {
  def hasOpenTournaments: Boolean = openTournaments.nonEmpty
  
  def isUserRegistered(tournament: Tournament): Boolean = {
    registrationStatus.getOrElse(tournament, false)
  }
  
  def availableTournaments: List[Tournament] = {
    openTournaments.filterNot(isUserRegistered)
  }
}

case class MatchDisplayData(
  tournament: Tournament,
  matchId: String,
  challengeMatchId: Long,
  opponent: String,
  status: MatchStatus,
  scheduledTime: Option[String],
  hasResult: Boolean,
  canUploadReplay: Boolean
)

object MatchDisplayData {
  def fromUserMatchInfo(matchInfo: UserMatchInfo): MatchDisplayData = {
    MatchDisplayData(
      tournament = matchInfo.tournament,
      matchId = matchInfo.matchId,
      challengeMatchId = matchInfo.challengeMatchId,
      opponent = matchInfo.opponent,
      status = MatchStatus.fromString(matchInfo.status),
      scheduledTime = matchInfo.scheduledTime,
      hasResult = matchInfo.winnerId.isDefined,
      canUploadReplay = matchInfo.status == "open"
    )
  }
}

sealed trait MatchStatus {
  def displayName: String
  def cssClass: String
}

object MatchStatus {
  case object Open extends MatchStatus {
    val displayName = "Open"
    val cssClass = "open"
  }
  
  case object Pending extends MatchStatus {
    val displayName = "Pending"
    val cssClass = "pending"
  }
  
  case object Complete extends MatchStatus {
    val displayName = "Complete"
    val cssClass = "complete"
  }
  
  case class Other(status: String) extends MatchStatus {
    val displayName: String = status.capitalize
    val cssClass = "other"
  }
  
  def fromString(status: String): MatchStatus = status.toLowerCase match {
    case "open" => Open
    case "pending" => Pending
    case "complete" => Complete
    case other => Other(other)
  }
}
