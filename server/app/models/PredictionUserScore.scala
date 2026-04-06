package models

import play.api.libs.json._

case class PredictionUserScore(
  userId: Long,
  eventId: Long,
  totalScore: Int = 0
)

object PredictionUserScore {
  implicit val predictionUserScoreFormat: OFormat[PredictionUserScore] = Json.format[PredictionUserScore]
}
