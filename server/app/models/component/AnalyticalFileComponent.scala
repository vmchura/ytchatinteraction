package models.component

import models.{AnalyticalFile, StarCraftModels}
import slick.jdbc.JdbcProfile
import java.time.Instant

trait AnalyticalFileComponent {
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

  class AnalyticalFilesTable(tag: Tag) extends Table[AnalyticalFile](tag, "analytical_files") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def userId = column[Long]("user_id")

    def sha256Hash = column[String]("sha256_hash")

    def originalName = column[String]("original_name")

    def relativeDirectoryPath = column[String]("relative_directory_path")

    def savedFileName = column[String]("saved_file_name")

    def uploadedAt = column[Instant]("uploaded_at")

    def slotPlayerId = column[Int]("slot_player_id")

    def userRace = column[StarCraftModels.SCRace]("user_race")

    def rivalRace = column[StarCraftModels.SCRace]("rival_race")

    def gameFrames = column[Int]("game_frames")

    def otherUserId = column[Option[Long]]("other_user_id")

    def algorithmId = column[Option[String]]("algorithm_id")

    def * = (id, userId, sha256Hash, originalName, relativeDirectoryPath, savedFileName, uploadedAt, slotPlayerId, userRace, rivalRace, gameFrames, otherUserId, algorithmId).mapTo[AnalyticalFile]

  }

  lazy val analyticalFilesTable = TableQuery[AnalyticalFilesTable]
}
