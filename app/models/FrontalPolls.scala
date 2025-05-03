package models

import play.api.libs.json._
import java.time.Instant

/**
 * Frontend JSON model for polls data
 */
case class PollData(
  title: String,
  isActive: Boolean,
  hasNoEndTime: Boolean,
  options: Seq[PollOptionData]
)

case class PollOptionData(
  optionText: String,
  optionProbability: BigDecimal,
  totalConfidence: Int
)

object PollData {
  implicit val pollOptionDataFormat: OFormat[PollOptionData] = Json.format[PollOptionData]
  implicit val pollDataFormat: OFormat[PollData] = Json.format[PollData]
}
