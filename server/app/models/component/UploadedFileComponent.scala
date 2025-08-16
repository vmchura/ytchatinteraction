package models.component

import models.UploadedFile
import slick.jdbc.JdbcProfile

import java.time.Instant

trait UploadedFileComponent {
  protected val profile: JdbcProfile
  
  import profile.api.*

  class UploadedFilesTable(tag: Tag) extends Table[UploadedFile](tag, "uploaded_files") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def tournamentId = column[Long]("tournament_id")
    def matchId = column[Long]("match_id")
    def sha256Hash = column[String]("sha256_hash")
    def originalName = column[String]("original_name")
    def relativeDirectoryPath = column[String]("relative_directory_path")
    def savedFileName = column[String]("saved_file_name")
    def uploadedAt = column[Instant]("uploaded_at")

    def * = (id, userId, tournamentId, matchId, sha256Hash, originalName, relativeDirectoryPath, savedFileName, uploadedAt).mapTo[UploadedFile]

    // Indexes
    def userIdIndex = index("idx_uploaded_files_user_id", userId)
    def tournamentIdIndex = index("idx_uploaded_files_tournament_id", tournamentId)
    def matchIdIndex = index("idx_uploaded_files_match_id", matchId)
    def sha256HashIndex = index("idx_uploaded_files_sha256_hash", sha256Hash, unique = true)
    def uploadedAtIndex = index("idx_uploaded_files_uploaded_at", uploadedAt)
  }

  lazy val uploadedFilesTable = TableQuery[UploadedFilesTable]
}