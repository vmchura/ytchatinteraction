package models

import play.api.libs.json._

case class PollOption(
  optionId: Option[Int] = None,
  pollId: Int,
  optionText: String,
  confidenceRatio: BigDecimal
)

object PollOption {
  implicit val pollOptionFormat: OFormat[PollOption] = Json.format[PollOption]
}
