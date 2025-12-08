package services

import evolutioncomplete.GameStateShared.*
import evolutioncomplete.WinnerShared.Cancelled
import evolutioncomplete.{ParticipantShared, TUploadStateShared}
import models.StarCraftModels.{SCMatchMode, Team}
import models._

import javax.inject.*
import java.util.concurrent.ConcurrentHashMap
import java.time.{Instant, LocalDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import play.api.Logger
import models.TournamentMatch
import models.repository.UserRepository
import models.StarCraftModels.ReplayParsed

import java.nio.file.Files

trait TUploadSessionService[F <: BasicFileInfo, SS <: TUploadStateShared[
  SS
], US <: TSessionUploadFile[US, F, SS], M <: TMetaSession](
    uploadedFileRepository: models.repository.UploadedFileRepository,
    tournamentService: TournamentService,
    userRepository: UserRepository,
    fileStorageService: FileStorageService
)(implicit ec: ExecutionContext) {
  private val logger = Logger(getClass)

  private val sessions = new ConcurrentHashMap[String, US]()

  // Session timeout in minutes
  private val sessionTimeoutMinutes = 30

  def persistState(uploadSession: US): US = {
    sessions.put(uploadSession.key, uploadSession)
    uploadSession
  }

  /** Start a new upload session for a user and match
    */
  def startSession(
      newSession: M
  ): Future[Option[US]]

  /** Get existing session or create a new one if it doesn't exist
    */
  def getOrCreateSession(
      potentialSession: M
  ): Future[Option[US]] = {
    Option(sessions.get(potentialSession.key)) match {
      case Some(existingSession) if !isSessionExpired(existingSession) =>
        logger.debug(
          s"Retrieved existing session: ${potentialSession}"
        )
        Future.successful(Some(existingSession))
      case Some(expiredSession) =>
        logger.info(
          s"Session expired: ${potentialSession}, removing it"
        )
        sessions.remove(potentialSession.key)
        Future.successful(None)
      case None =>
        logger.info(
          s"No session found, creating new one for potential Session: ${potentialSession}"
        )
        startSession(potentialSession)
    }
  }

  def getSession(
      potentialSession: M
  ): Option[US] = {
    Option(sessions.get(potentialSession.key)) match {
      case Some(existingSession) if !isSessionExpired(existingSession) =>
        logger.debug(
          s"Retrieved existing session: ${potentialSession}"
        )
        Some(existingSession)
      case Some(expiredSession) =>
        logger.info(
          s"Session expired: ${potentialSession} removing it"
        )
        sessions.remove(potentialSession.key)
        None
      case None =>
        logger.info(s"No session found")
        None
    }
  }

  /** Add a file to an existing session with duplicate detection (both local and
    * global)
    */
  def addFileToSession(
      currentSession: US,
      fileResult: FileProcessResult
  ): Future[US] = {

    // Check for duplicates before adding
    checkForDuplicateFile(fileResult).map { isDuplicate =>
      if (isDuplicate) {
        logger.info(
          s"File ${fileResult.fileName} with SHA256 ${fileResult.sha256Hash.getOrElse("unknown")} already exists globally, skipping"
        )
        currentSession.withFullCopyStateShared(
          currentSession.uploadState.updateOnePendingTo(uuid =>
            InvalidGame("Duplicado en otras partidas", uuid)
          )
        )
      } else {
        fileResult match {
          case FileProcessResult(_, _, _, _, _, errorMessage, _, None, _) =>
            currentSession.withFullCopyStateShared(
              currentSession.uploadState.updateOnePendingTo(uuid =>
                InvalidGame(
                  errorMessage.getOrElse("No Hash, archivo corrupto"),
                  uuid
                )
              )
            )
          case FileProcessResult(_, _, _, _, _, _, _, Some(sha256Hash), _)
              if currentSession.uploadState.games.exists {
                case ValidGame(_, _, _, hash, _) if hash.equals(sha256Hash) =>
                  true
                case _ => false
              } =>
            currentSession.withFullCopyStateShared(
              currentSession.uploadState.updateOnePendingTo(uuid =>
                InvalidGame("Duplicado en esta sesión", uuid)
              )
            )
          case FileProcessResult(
                fileName,
                originalSize,
                contentType,
                processedAt,
                success,
                _,
                Some(
                  ReplayParsed(
                    Some(mapName),
                    Some(startTime),
                    _,
                    teams,
                    _,
                    _,
                    _
                  )
                ),
                Some(sha256Hash),
                path
              ) =>
            fileStorageService.storeBasicFile(
              Files.readAllBytes(path),
              fileName,
              fileResult.contentType,
              currentSession.userId,
              currentSession
            ) match {
              case Left(error) =>
                currentSession.withFullCopyStateShared(
                  currentSession.uploadState.updateOnePendingTo(uuid =>
                    InvalidGame(error, uuid)
                  )
                )
              case Right(storedInfo) =>
                currentSession
                  .withFullCopyStateShared(
                    currentSession.uploadState
                      .updateOnePendingTo(uuid =>
                        ValidGame(
                          teams.flatMap(_.participants.map(_.name)),
                          mapName,
                          LocalDateTime.now(),
                          sha256Hash,
                          uuid
                        )
                      )
                      .calculateWinner()
                  )
                  .withNewHash(
                    sha256Hash,
                    storedInfo
                  )
            }
          case FileProcessResult(_, _, _, _, _, errorMessage, _, _, _) =>
            currentSession.withFullCopyStateShared(
              currentSession.uploadState.updateOnePendingTo(uuid =>
                InvalidGame(errorMessage.getOrElse("Otro error"), uuid)
              )
            )
        }
      }

    }
  }

  /** Check if a file already exists globally in the UploadedFileRepository
    */
  private def checkForDuplicateFile(
      fileResult: FileProcessResult
  ): Future[Boolean] = {
    fileResult.sha256Hash match {
      case Some(sha256) =>
        val r = uploadedFileRepository.findBySha256Hash(sha256)
        r.map(_.isDefined)
      case None =>
        Future.successful(false)
    }
  }

  /** Remove a specific file from session by SHA256 hash
    */
  def removeFileFromSession(
      currentSession: US,
      sessionUUID: UUID
  ): US = {
    currentSession
      .withFullCopyStateShared(
        currentSession.uploadState
          .withGames(currentSession.uploadState.games.filter {
            _.sessionID.compareTo(sessionUUID) != 0
          })
          .calculateWinner()
      )

  }

  /** Get all active sessions (for debugging/monitoring)
    */
  def getAllActiveSessions: List[US] = {
    val activeSessions = sessions.asScala.values
      .filter(session => !isSessionExpired(session))
      .toList

    // Clean up expired sessions
    cleanupExpiredSessions()

    activeSessions
  }

  /** Check if a session has expired
    */
  private def isSessionExpired(session: US): Boolean = {
    val timeoutMillis = sessionTimeoutMinutes * 60 * 1000
    val now = Instant.now()

    session.lastUpdated.plusMillis(timeoutMillis).isBefore(now)
  }

  /** Clean up expired sessions
    */
  private def cleanupExpiredSessions(): Unit = {
    val expiredKeys = sessions.asScala
      .filter { case (_, session) =>
        isSessionExpired(session)
      }
      .keys
      .toList

    expiredKeys.foreach { key =>
      sessions.remove(key)
      logger.debug(s"Cleaned up expired session for key: $key")
    }

    if (expiredKeys.nonEmpty) {
      logger.info(s"Cleaned up ${expiredKeys.length} expired sessions")
    }
  }

  def finalizeSession(session: US): US = {
    persistState(session.finalizeSession())
  }
}
