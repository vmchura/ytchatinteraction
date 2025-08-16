package models

import play.api.libs.json._

case class EventPoll(
  pollId: Option[Int] = None,
  eventId: Int,
  pollQuestion: String,
  winnerOptionId: Option[Int] = None
)

object EventPoll {
  implicit val eventPollFormat: OFormat[EventPoll] = Json.format[EventPoll]
}
