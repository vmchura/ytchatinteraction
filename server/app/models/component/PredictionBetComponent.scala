package models.component

import models.PredictionBet
import play.api.libs.json.Json
import slick.ast.BaseTypedType
import slick.jdbc.JdbcProfile

import java.time.Instant

trait PredictionBetComponent {
  protected val profile: JdbcProfile

  import profile.api.*

  given BaseTypedType[Seq[String]] =
    MappedColumnType.base[Seq[String], String](
      seq => Json.toJson(seq).toString(),
      str => Json.parse(str).as[Seq[String]]
    )

  class PredictionBetsTable(tag: Tag)
      extends Table[PredictionBet](tag, "prediction_bets") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def matchupId = column[Long]("matchup_id")
    def ranking = column[Seq[String]]("ranking")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")

    def * = (id, userId, matchupId, ranking, createdAt, updatedAt).mapTo[PredictionBet]
  }

  lazy val predictionBetsTable = TableQuery[PredictionBetsTable]
}
