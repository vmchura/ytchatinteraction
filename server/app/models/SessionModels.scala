package models
import java.time.Instant

trait BasicFileInfo:
  def originalFileName: String
  def storedFileName: String
  def storedPath: String
  def size: Long
  def contentType: String
  def storedAt: Instant
  def userId: Long

case class GenericFileInfo(
    originalFileName: String,
    storedFileName: String,
    storedPath: String,
    size: Long,
    contentType: String,
    storedAt: Instant,
    userId: Long
) extends BasicFileInfo

case class StoredFileInfo(
    originalFileName: String,
    storedFileName: String,
    storedPath: String,
    size: Long,
    contentType: String,
    storedAt: Instant,
    userId: Long,
    matchId: Long,
    sessionId: String
) extends BasicFileInfo

case class AnalyticalFileInfo(
    originalFileName: String,
    storedFileName: String,
    storedPath: String,
    size: Long,
    contentType: String,
    storedAt: Instant,
    userId: Long
) extends BasicFileInfo

case class CasualMatchFileInfo(
    originalFileName: String,
    storedFileName: String,
    storedPath: String,
    size: Long,
    contentType: String,
    storedAt: Instant,
    userId: Long,
    casualMatchId: Long
) extends BasicFileInfo

trait TSessionUploadFile[F <: BasicFileInfo]:
  def key: String
  def fromGenericFileInfo(
      genericFileInfo: GenericFileInfo
  ): F

case class TournamentSession(userId: Long, matchId: Long, tournamentId: Long)
    extends TSessionUploadFile[StoredFileInfo]:
  override def key: String = s"${userId}_${matchId}_${tournamentId}"
  override def fromGenericFileInfo(
      genericFileInfo: GenericFileInfo
  ): StoredFileInfo = {
    genericFileInfo match {
      case GenericFileInfo(
            originalFileName,
            storedFileName,
            storedPath,
            size,
            contentType,
            storedAt,
            userId
          ) =>
        StoredFileInfo(
          originalFileName,
          storedFileName,
          storedPath,
          size,
          contentType,
          storedAt,
          userId,
          matchId,
          key
        )
    }
  }

case class AnalyticalSession(userId: Long)
    extends TSessionUploadFile[AnalyticalFileInfo]:
  override def key: String = s"${userId}"
  override def fromGenericFileInfo(
      genericFileInfo: GenericFileInfo
  ): AnalyticalFileInfo = {
    genericFileInfo match {
      case GenericFileInfo(
            originalFileName,
            storedFileName,
            storedPath,
            size,
            contentType,
            storedAt,
            userId
          ) =>
        AnalyticalFileInfo(
          originalFileName,
          storedFileName,
          storedPath,
          size,
          contentType,
          storedAt,
          userId
        )
    }
  }
