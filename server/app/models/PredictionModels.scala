package models

import play.api.libs.json._

case class PredictionUserScoreWithUsername(
  userId: Long,
  eventId: Long,
  totalScore: Int,
  userName: String
)

object PredictionUserScoreWithUsername {
  implicit val predictionUserScoreWithUsernameFormat: OFormat[PredictionUserScoreWithUsername] = Json.format[PredictionUserScoreWithUsername]
}

/**
 * Body for saving a user's prediction for a matchup.
 *
 * `ranking` is the ordered list of player slot keys (GroupPosition) or a
 * single-element list with the chosen score (MatchScore).
 */
case class SavePredictionRequest(
  matchupId: Long,
  ranking: Seq[String]
)

object SavePredictionRequest {
  implicit val savePredictionRequestFormat: OFormat[SavePredictionRequest] = Json.format[SavePredictionRequest]
}
