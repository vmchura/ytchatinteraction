package models.component

import models.{AnalyticalResult, StarCraftModels}
import slick.jdbc.JdbcProfile
import java.time.Instant

trait AnalyticalResultComponent {
  protected val profile: JdbcProfile

  import profile.api._

  given BaseColumnType[StarCraftModels.SCRace] =
    MappedColumnType.base[StarCraftModels.SCRace, String](
      {
        case StarCraftModels.Zerg => "Zerg"
        case StarCraftModels.Terran => "Terran"
        case StarCraftModels.Protoss => "Protoss"
      },
      {
        case "Zerg" => StarCraftModels.Zerg
        case "Terran" => StarCraftModels.Terran
        case "Protoss" => StarCraftModels.Protoss
      }
    )

  class AnalyticalResultsTable(tag: Tag) extends Table[AnalyticalResult](tag, "analytical_result") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def userId = column[Long]("user_id")

    def matchId = column[Long]("match_id")

    def userRace = column[StarCraftModels.SCRace]("userrace")

    def rivalRace = column[StarCraftModels.SCRace]("rivalrace")

    def originalFileName = column[String]("originalfilename")

    def analysisStartedAt = column[Instant]("analysis_started_at")

    def analysisFinishedAt = column[Instant]("analysis_finished_at")

    def algorithmVersion = column[String]("algorith_version")

    def result = column[Option[Boolean]]("result")

    def * = (id, userId, matchId, userRace, rivalRace, originalFileName, analysisStartedAt, analysisFinishedAt, algorithmVersion, result).mapTo[AnalyticalResult]

    def userIdIndex = index("idx_analytical_result_user_id", userId)

    def matchIdIndex = index("idx_analytical_result_match_id", matchId)
  }

  lazy val analyticalResultsTable = TableQuery[AnalyticalResultsTable]
}