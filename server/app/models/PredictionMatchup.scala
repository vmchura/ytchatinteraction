package models

import play.api.libs.json._
import java.time.Instant

case class PredictionMatchup(
  id: Long = 0L,
  eventId: Long,
  round: Int,
  matchLabel: String,
  betType: PredictionBetType,
  player1Name: Option[String] = None,
  player1Race: Option[String] = None,
  player2Name: Option[String] = None,
  player2Race: Option[String] = None,
  player3Name: Option[String] = None,
  player3Race: Option[String] = None,
  player4Name: Option[String] = None,
  player4Race: Option[String] = None,
  result: Option[JsValue] = None,
  status: PredictionMatchupStatus = PredictionMatchupStatus.Open,
  resultScoredAt: Option[Instant] = None
)

sealed trait PredictionBetType

object PredictionBetType {
  case object GroupPosition extends PredictionBetType
  case object MatchScore extends PredictionBetType

  def fromString(s: String): PredictionBetType = s match {
    case "GroupPosition" => GroupPosition
    case "MatchScore"    => MatchScore
    case other           => throw new IllegalArgumentException(s"Unknown bet type: $other")
  }

  def fromStringOpt(s: String): Option[PredictionBetType] = s match {
    case "GroupPosition" => Some(GroupPosition)
    case "MatchScore"    => Some(MatchScore)
    case _               => None
  }
}

sealed trait PredictionMatchupStatus

object PredictionMatchupStatus {
  case object Open extends PredictionMatchupStatus
  case object Locked extends PredictionMatchupStatus
  case object Scored extends PredictionMatchupStatus

  def fromString(s: String): PredictionMatchupStatus = s match {
    case "Open"   => Open
    case "Locked" => Locked
    case "Scored" => Scored
    case other    => throw new IllegalArgumentException(s"Unknown prediction matchup status: $other")
  }
}
