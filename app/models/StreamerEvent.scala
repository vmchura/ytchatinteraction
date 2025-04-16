package models

import play.api.libs.json._
import java.time.Instant

case class StreamerEvent(
  eventId: Option[Int] = None,
  channelId: String,
  eventName: String,
  eventDescription: Option[String] = None,
  eventType: String,
  currentConfidenceAmount: Long,
  isActive: Boolean = true,
  startTime: Instant = Instant.now(),
  endTime: Option[Instant] = None,
  createdAt: Option[Instant] = None,
  updatedAt: Option[Instant] = None
)

object StreamerEvent {
  implicit val streamerEventFormat: OFormat[StreamerEvent] = Json.format[StreamerEvent]
  
  // Event type constants
  val TYPE_OFFLINE = "OFFLINE"
  val TYPE_LIVE = "LIVE"
  val TYPE_SCHEDULED = "SCHEDULED"
}
