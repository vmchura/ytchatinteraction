package models

import java.time.Instant

case class UploadedFile(
  id: Long = 0L,
  userId: Long,
  tournamentId: Long,
  matchId: Long,
  sha256Hash: String,
  originalName: String,
  relativeDirectoryPath: String,
  savedFileName: String,
  uploadedAt: Instant
)