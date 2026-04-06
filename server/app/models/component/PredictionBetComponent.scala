package models.component

import models.PredictionBet
import slick.jdbc.JdbcProfile

import java.time.Instant

trait PredictionBetComponent {
  protected val profile: JdbcProfile

  import profile.api.*

  class PredictionBetsTable(tag: Tag)
      extends Table[PredictionBet](tag, "prediction_bets") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def matchupId = column[Long]("matchup_id")
    def selection = column[String]("selection")
    def pointsBet = column[Int]("points_bet")
    def createdAt = column[Instant]("created_at")

    def * = (id, userId, matchupId, selection, pointsBet, createdAt).mapTo[PredictionBet]
  }

  lazy val predictionBetsTable = TableQuery[PredictionBetsTable]
}
