package models.component

import models.{CasualMatchFile, ServerStarCraftModels, StarCraftModels}
import slick.jdbc.JdbcProfile

import java.time.Instant

trait CasualMatchFileComponent {
  protected val profile: JdbcProfile
  
  import profile.api._

  given BaseColumnType[StarCraftModels.SCRace] = ServerStarCraftModels.scRaceColumnType

  class CasualMatchFilesTable(tag: Tag) extends Table[CasualMatchFile](tag, "casual_match_files") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def casualMatchId = column[Long]("casual_match_id")
    def sha256Hash = column[String]("sha256_hash")
    def originalName = column[String]("original_name")
    def relativeDirectoryPath = column[String]("relative_directory_path")
    def savedFileName = column[String]("saved_file_name")
    def uploadedAt = column[Instant]("uploaded_at")
    def slotPlayerId = column[Int]("slot_player_id")
    def rivalSlotPlayerId = column[Int]("rival_slot_player_id")
    def userRace = column[StarCraftModels.SCRace]("user_race")
    def rivalRace = column[StarCraftModels.SCRace]("rival_race")
    def gameFrames = column[Int]("game_frames")

    def * = (id, casualMatchId, sha256Hash, originalName, relativeDirectoryPath, savedFileName, uploadedAt, slotPlayerId, rivalSlotPlayerId, userRace, rivalRace, gameFrames).mapTo[CasualMatchFile]

  }

  lazy val casualMatchFilesTable = TableQuery[CasualMatchFilesTable]
}
