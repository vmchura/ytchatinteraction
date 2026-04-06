package models.component

import models.PredictionUserScore
import slick.jdbc.JdbcProfile

trait PredictionUserScoreComponent {
  protected val profile: JdbcProfile

  import profile.api.*

  class PredictionUserScoresTable(tag: Tag)
      extends Table[PredictionUserScore](tag, "prediction_user_scores") {
    def userId = column[Long]("user_id", O.PrimaryKey)
    def eventId = column[Long]("event_id", O.PrimaryKey)
    def totalScore = column[Int]("total_score")

    def * = (userId, eventId, totalScore).mapTo[PredictionUserScore]
  }

  lazy val predictionUserScoresTable = TableQuery[PredictionUserScoresTable]
}
