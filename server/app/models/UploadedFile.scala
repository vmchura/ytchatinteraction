package models

import java.time.Instant

trait GenericUploadedFile:
  def id: Long
  def sha256Hash: String
  def originalName: String
  def relativeDirectoryPath: String
  def savedFileName: String
  def uploadedAt: Instant

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
) extends GenericUploadedFile