package services

import evolutioncomplete.GameStateShared.*
import evolutioncomplete.WinnerShared.Cancelled
import evolutioncomplete.{ParticipantShared, UploadStateShared}
import models.StarCraftModels.{ReplayParsed, SCMatchMode, SCRace, Team}
import models.{MatchStatus, StarCraftModels, User}

import javax.inject.*
import java.util.concurrent.ConcurrentHashMap
import java.time.{Instant, LocalDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import play.api.Logger
import models.repository.UserRepository

import java.nio.file.Files


case class AnalyticalUploadSession(
                                    userId: Long,
                                    fileResult: FileProcessResult,
                                    createdAt: Instant,
                                    lastUpdated: Instant,
                                    storageInfo: AnalyticalFileInfo,
                                    isFinalized: Boolean = false
                                  ) {
  val players: Seq[StarCraftModels.SCPlayer] = fileResult.gameInfo match {
    case Some(ReplayParsed(_, _, _, teams, _, _)) =>

      val all = teams.flatMap(_.participants)
      if (all.length == 2) {
        all
      } else {
        List.empty
      }
    case _ => List.empty
  }
  val sha256Hash: String = fileResult.sha256Hash.getOrElse(UUID.randomUUID().toString)

  def userRaceGivenPlayerId(playerId: Int): Option[SCRace] = players.find(_.id == playerId).map(p => p.race)

  def rivalRaceGivenPlayerId(playerId: Int): Option[SCRace] = players.find(_.id != playerId).map(p => p.race)

  val frames: Option[Int] = fileResult.gameInfo match {
    case Some(ReplayParsed(_, _, _, _, _, Some(frames))) if frames > 12_000 =>
      Some(frames)
    case _ => None
  }
  val isValid: Boolean = players.nonEmpty && fileResult.sha256Hash.isDefined && frames.isDefined

}

@Singleton
class AnalyticalUploadSessionService @Inject()(
                                                uploadedFileRepository: models.repository.UploadedFileRepository,
                                                analyticalFileRepository: models.repository.AnalyticalFileRepository,
                                                userRepository: UserRepository,
                                                fileStorageService: FileStorageService
                                              )(implicit ec: ExecutionContext) {

  private val logger = Logger(getClass)
  private val sessions = new ConcurrentHashMap[Long, AnalyticalUploadSession]()

  // Session timeout in minutes
  private val sessionTimeoutMinutes = 30

  def persistState(uploadSession: AnalyticalUploadSession): AnalyticalUploadSession = {
    sessions.put(uploadSession.userId, uploadSession)
    uploadSession
  }


  /**
   * Get existing session or create a new one if it doesn't exist
   */
  def getSession(user: User): Option[AnalyticalUploadSession] = {
    Option(sessions.get(user.userId)) match {
      case Some(existingSession) if !isSessionExpired(existingSession) =>
        Some(existingSession)
      case Some(expiredSession) =>
        sessions.remove(user.userId)
        None
      case None =>
        None
    }
  }

  def startSession(user: User, fileResult: FileProcessResult): Future[Option[AnalyticalUploadSession]] = {

    checkForDuplicateFile(fileResult).flatMap { isDuplicate =>
      if (isDuplicate) {
        logger.info(s"File ${fileResult.fileName} with SHA256 ${fileResult.sha256Hash.getOrElse("unknown")} already exists globally, skipping")
        Future.successful(None)
      } else {

        fileResult match {
          case FileProcessResult(_, _, _, _, _, errorMessage, _, None, _) =>
            Future.successful(None)
          case FileProcessResult(fileName, originalSize, contentType, processedAt, success, _, Some(ReplayParsed(
          Some(mapName), Some(startTime), _, teams, _, _)), Some(sha256Hash), path) =>
            fileStorageService.storeAnalyticalFile(Files.readAllBytes(path), fileName,
              fileResult.contentType, user.userId).map {
              case Left(error) =>
                None
              case Right(storedInfo) =>
                val newSession = AnalyticalUploadSession(
                  userId = user.userId,
                  fileResult = fileResult,
                  createdAt = Instant.now(),
                  lastUpdated = Instant.now(),
                  storageInfo = storedInfo
                )
                Option.when(newSession.isValid)(persistState(newSession))
            }
          case FileProcessResult(_, _, _, _, _, errorMessage, _, _, _) => Future.successful(None)
        }
      }


    }
  }


  /**
   * Check if a file already exists globally in the UploadedFileRepository
   */
  private def checkForDuplicateFile(fileResult: FileProcessResult): Future[Boolean] = {
    fileResult.sha256Hash match {
      case Some(sha256) =>
        for {
          duplicateByTournaments <- uploadedFileRepository.findBySha256Hash(sha256)
          duplicateByAnalytical <- analyticalFileRepository.findBySha256Hash(sha256)
        } yield {
          duplicateByTournaments.isDefined || duplicateByAnalytical.isDefined
        }
      case None =>
        Future.successful(false)
    }
  }

  /**
   * Get all active sessions (for debugging/monitoring)
   */
  def getAllActiveSessions: List[AnalyticalUploadSession] = {
    val activeSessions = sessions.asScala.values
      .filter(session => !isSessionExpired(session))
      .toList

    // Clean up expired sessions
    cleanupExpiredSessions()

    activeSessions
  }


  /**
   * Check if a session has expired
   */
  private def isSessionExpired(session: AnalyticalUploadSession): Boolean = {
    val timeoutMillis = sessionTimeoutMinutes * 60 * 1000
    val now = Instant.now()

    session.lastUpdated.plusMillis(timeoutMillis).isBefore(now)
  }

  /**
   * Clean up expired sessions
   */
  private def cleanupExpiredSessions(): Unit = {
    val expiredKeys = sessions.asScala.filter { case (_, session) =>
      isSessionExpired(session)
    }.keys.toList

    expiredKeys.foreach { key =>
      sessions.remove(key)
      logger.debug(s"Cleaned up expired session for key: $key")
    }

    if (expiredKeys.nonEmpty) {
      logger.info(s"Cleaned up ${expiredKeys.length} expired sessions")
    }
  }

  def finalizeSession(session: AnalyticalUploadSession): AnalyticalUploadSession = {
    persistState(session.copy(isFinalized = true))
  }
}
