package models

import play.api.libs.json._
import java.time.Instant

case class PredictionBet(
  id: Long = 0L,
  userId: Long,
  matchupId: Long,
  selection: String,
  pointsBet: Int,
  createdAt: Instant = Instant.now()
)

object PredictionBet {
  implicit val predictionBetFormat: OFormat[PredictionBet] = Json.format[PredictionBet]
}
