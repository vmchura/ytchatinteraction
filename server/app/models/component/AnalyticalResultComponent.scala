package models.component

import models.{AnalyticalResult, ServerStarCraftModels, StarCraftModels}
import slick.jdbc.JdbcProfile

import java.time.Instant

trait AnalyticalResultComponent {
  protected val profile: JdbcProfile

  import profile.api._

  given BaseColumnType[StarCraftModels.SCRace] = ServerStarCraftModels.scRaceColumnType

  class AnalyticalResultsTable(tag: Tag) extends Table[AnalyticalResult](tag, "analytical_result") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def userId = column[Long]("user_id")

    def matchId = column[Option[Long]]("match_id")

    def userRace = column[StarCraftModels.SCRace]("userrace")

    def rivalRace = column[StarCraftModels.SCRace]("rivalrace")

    def originalFileName = column[String]("originalfilename")

    def analysisStartedAt = column[Instant]("analysis_started_at")

    def analysisFinishedAt = column[Option[Instant]]("analysis_finished_at")

    def algorithmVersion = column[Option[String]]("algorith_version")

    def result = column[Option[Boolean]]("result")

    def casualMatchID = column[Option[Long]]("casual_match_id")

    def * = (id, userId, matchId, userRace, rivalRace, originalFileName, analysisStartedAt, analysisFinishedAt, algorithmVersion, result, casualMatchID).mapTo[AnalyticalResult]

    def userIdIndex = index("idx_analytical_result_user_id", userId)

    def matchIdIndex = index("idx_analytical_result_match_id", matchId)
  }

  lazy val analyticalResultsTable = TableQuery[AnalyticalResultsTable]
}