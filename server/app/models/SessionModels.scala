package models
import java.time.Instant
import evolutioncomplete._
import GameStateShared._

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

trait TSessionUploadFile[
    US <: TSessionUploadFile[US, F, SS],
    F <: BasicFileInfo,
    SS <: TUploadStateShared[SS]
] { this: US =>
  def userId: Long
  def key: String
  def fromGenericFileInfo(
      genericFileInfo: GenericFileInfo
  ): F
  def finalizeSession(): US
  def uploadState: SS
  def lastUpdated: Instant
  def withUploadStateShared(uploadStateShared: SS): US
  def withFullCopyStateShared(uploadStateShared: SS): US
  def hash2StoreInformation: Map[String, F]
  def withNewHash(hash: String, newFile: F): US
}

case class TournamentSession(
    userId: Long,
    matchId: Long,
    tournamentId: Long,
    uploadState: TournamentUploadStateShared,
    hash2StoreInformation: Map[String, StoredFileInfo],
    lastUpdated: Instant
) extends TSessionUploadFile[
      TournamentSession,
      StoredFileInfo,
      TournamentUploadStateShared
    ]:
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

  def withUploadStateShared(
      uploadStateShared: TournamentUploadStateShared
  ): TournamentSession = {
    copy(uploadState =
      uploadState.copy(
        games = uploadState.games ++ uploadStateShared.games.filter {
          case PendingGame(_) => true
          case _              => false
        },
        winner = uploadStateShared.winner,
        firstParticipant = uploadState.firstParticipant
          .copy(smurfs = uploadStateShared.firstParticipant.smurfs),
        secondParticipant = uploadState.secondParticipant.copy(smurfs =
          uploadStateShared.secondParticipant.smurfs
        )
      )
    )

  }

  override def withFullCopyStateShared(
      uploadStateShared: TournamentUploadStateShared
  ): TournamentSession =
    copy(uploadState = uploadStateShared, lastUpdated = java.time.Instant.now())

  override def withNewHash(
      hash: String,
      newFile: StoredFileInfo
  ): TournamentSession =
    copy(hash2StoreInformation = hash2StoreInformation + (hash -> newFile))

case class AnalyticalSession(
    userId: Long,
    uploadState: AnalyticalUploadStateShared,
    hash2StoreInformation: Map[String, AnalyticalFileInfo],
    lastUpdated: Instant
) extends TSessionUploadFile[
      AnalyticalSession,
      AnalyticalFileInfo,
      AnalyticalUploadStateShared
    ]:
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
  override def withUploadStateShared(
      uploadStateShared: AnalyticalUploadStateShared
  ): AnalyticalSession = {
    copy(uploadState =
      uploadState.copy(
        games = uploadState.games ++ uploadStateShared.games.filter {
          case PendingGame(_) => true
          case _              => false
        },
        firstParticipant = uploadState.firstParticipant
          .copy(smurfs = uploadStateShared.firstParticipant.smurfs)
      )
    )

  }

  override def withFullCopyStateShared(
      uploadStateShared: AnalyticalUploadStateShared
  ): AnalyticalSession =
    copy(uploadState = uploadStateShared, lastUpdated = java.time.Instant.now())

  override def withNewHash(
      hash: String,
      newFile: AnalyticalFileInfo
  ): AnalyticalSession =
    copy(hash2StoreInformation = hash2StoreInformation + (hash -> newFile))
