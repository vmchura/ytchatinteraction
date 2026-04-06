package models

import play.api.libs.json._
import java.time.Instant

case class PredictionEvent(
  id: Long = 0L,
  name: String,
  status: PredictionEventStatus = PredictionEventStatus.Open,
  createdAt: Instant = Instant.now()
)

sealed trait PredictionEventStatus

object PredictionEventStatus {
  case object Open extends PredictionEventStatus
  case object Locked extends PredictionEventStatus
  case object Completed extends PredictionEventStatus

  def fromString(s: String): PredictionEventStatus = s match {
    case "Open"      => Open
    case "Locked"    => Locked
    case "Completed" => Completed
    case other       => throw new IllegalArgumentException(s"Unknown prediction event status: $other")
  }
}
