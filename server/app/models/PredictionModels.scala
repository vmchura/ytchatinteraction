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

case class PlaceBetRequest(
  matchupId: Long,
  selection: String,
  pointsBet: Int
)

object PlaceBetRequest {
  implicit val placeBetRequestFormat: OFormat[PlaceBetRequest] = Json.format[PlaceBetRequest]
}

case class PlaceBetsRequest(
  bets: Seq[PlaceBetRequest]
)

object PlaceBetsRequest {
  implicit val placeBetsRequestFormat: OFormat[PlaceBetsRequest] = Json.format[PlaceBetsRequest]
}

case class GroupResultRequest(
  positions: Map[String, String]
)

object GroupResultRequest {
  implicit val groupResultRequestFormat: OFormat[GroupResultRequest] = Json.format[GroupResultRequest]
}

case class MatchResultRequest(
  score: String
)

object MatchResultRequest {
  implicit val matchResultRequestFormat: OFormat[MatchResultRequest] = Json.format[MatchResultRequest]
}
