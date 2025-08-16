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
  def fromProbabilityWinRate(probabilityToWin: BigDecimal): BigDecimal = {
    ((1.0f/(probabilityToWin*(1+0.15)))*100.0).toInt/100.0
  }
}
