package services

import evolutioncomplete.GameStateShared.*
import evolutioncomplete.WinnerShared.Undefined
import evolutioncomplete.{ParticipantShared, UploadStateShared}
import models.StarCraftModels.{SCMatchMode, Team}
import models.{MatchStatus, User}

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



/**
 * Key for identifying upload sessions by user and match
 */
case class SessionKey(userId: Long, matchId: Long, tournamentId: Long) {
  override def toString: String = s"${userId}_${matchId}_$tournamentId"
}
/**
 * Represents an upload session for a specific user and match
 */
case class UploadSession(
                          sessionId: SessionKey,
                          challongeMatchID: Long,
                          userId: Long,
                          uploadState: UploadStateShared,
                          createdAt: Instant,
                          lastUpdated: Instant,
                          isFinalized: Boolean = false,
                          hash2StoreInformation: Map[String, StoredFileInfo] = Map.empty
                        ) {

  def finalizeSession(): UploadSession = {
    copy(
      isFinalized = true,
      lastUpdated = Instant.now()
    )
  }
  def withUploadStateShared(uploadStateShared: UploadStateShared): UploadSession = {
    copy(uploadState = uploadState.copy(games = uploadState.games ++ uploadStateShared.games.filter{
      case PendingGame(_) => true
      case _ => false
    }, winner = uploadStateShared.winner,
      firstParticipant = uploadState.firstParticipant.copy(smurfs = uploadStateShared.firstParticipant.smurfs),
      secondParticipant = uploadState.secondParticipant.copy(smurfs = uploadStateShared.secondParticipant.smurfs)))
  }
}
/**
 * In-memory service for managing upload sessions
 */
@Singleton
class UploadSessionService @Inject()(
                                      uploadedFileRepository: models.repository.UploadedFileRepository,
                                      tournamentService: TournamentService,
                                      userRepository: UserRepository,
                                      fileStorageService: FileStorageService
                                    )(implicit ec: ExecutionContext) {

  private val logger = Logger(getClass)
  private val sessions = new ConcurrentHashMap[SessionKey, UploadSession]()

  // Session timeout in minutes
  private val sessionTimeoutMinutes = 30
  def persistState(uploadSession: UploadSession): UploadSession = {
    sessions.put(uploadSession.sessionId, uploadSession)
    uploadSession
  }
  /**
   * Start a new upload session for a user and match
   */
  def startSession(user: User, challongeMatchID: Long, tournamentId: Long): Future[Option[UploadSession]] = {
    val sessionKey = SessionKey(user.userId, challongeMatchID, tournamentId)
    val futSession = tournamentService.getMatch(tournamentId, challongeMatchID).flatMap {
      case Some(TournamentMatch(_, _, firstUserId, secondUserId, winnerUserId, createdAt, MatchStatus.Pending | MatchStatus.InProgress)) =>
        for {
          firstUserOpt <- userRepository.getById(firstUserId)
          secondUserOpt <- userRepository.getById(secondUserId)
        } yield {
          firstUserOpt.zip(secondUserOpt).map { (firstUser, secondUser) =>
            val session = UploadSession(
              sessionId = sessionKey,
              challongeMatchID = challongeMatchID,
              userId = user.userId,
              uploadState = UploadStateShared(challongeMatchID = challongeMatchID, tournamentID = tournamentId, firstParticipant = ParticipantShared(firstUser.userId, firstUser.userName, Set.empty[String]), secondParticipant = ParticipantShared(secondUser.userId, secondUser.userName, Set.empty[String]), games = Nil, winner = Undefined),
              createdAt = Instant.now(),
              lastUpdated = Instant.now()
            )
            persistState(session)
          }

        }
      case _ => Future.successful(None)
    }

    futSession
  }

  /**
   * Get existing session or create a new one if it doesn't exist
   */
  def getOrCreateSession(user: User, challongeMatchID: Long, tournamentId: Long): Future[Option[UploadSession]] = {
      val sessionKey = SessionKey(user.userId, challongeMatchID, tournamentId)
      Option(sessions.get(sessionKey)) match {
        case Some(existingSession) if !isSessionExpired(existingSession) =>
          logger.debug(s"Retrieved existing session: ${existingSession.sessionId}")
          Future.successful(Some(existingSession))
        case Some(expiredSession) =>
          logger.info(s"Session expired: ${expiredSession.sessionId}, creating new one")
          sessions.remove(sessionKey)
          Future.successful(None)
        case None =>
          println(s"No session found, creating new one for user: ${user.userId}, challongeMatchID: $challongeMatchID")
          logger.info(s"No session found, creating new one for user: ${user.userId}, challongeMatchID: $challongeMatchID")
          startSession(user, challongeMatchID, tournamentId)
      }
  }

  /**
   * Add a file to an existing session with duplicate detection (both local and global)
   */
  def addFileToSession(currentSession: UploadSession, fileResult: FileProcessResult): Future[UploadSession] = {

    // Check for duplicates before adding
    checkForDuplicateFile(fileResult).map { isDuplicate =>
      if (isDuplicate) {
        logger.info(s"File ${fileResult.fileName} with SHA256 ${fileResult.sha256Hash.getOrElse("unknown")} already exists globally, skipping")
        currentSession.copy(lastUpdated = java.time.Instant.now(),
          uploadState = currentSession.uploadState.updateOnePendingTo(uuid => InvalidGame("Duplicado en otras partidas", uuid)))
      } else {
        fileResult match {
          case FileProcessResult(_, _, _, _, _, errorMessage, _, None, _) =>
            currentSession.copy(lastUpdated = java.time.Instant.now(),
              uploadState = currentSession.uploadState.updateOnePendingTo(uuid => InvalidGame(errorMessage.getOrElse("No Hash, archivo corrupto"), uuid)))
          case FileProcessResult(_, _, _, _, _, _, _, Some(sha256Hash), _) if currentSession.uploadState.games.exists {
            case ValidGame(_, _, _, hash, _) if hash.equals(sha256Hash) => true
            case _ => false
          } => currentSession.copy(lastUpdated = java.time.Instant.now(),
            uploadState = currentSession.uploadState.updateOnePendingTo(uuid => InvalidGame("Duplicado en esta sesión", uuid)))
          case FileProcessResult(fileName, originalSize, contentType, processedAt, success, _, Some(ReplayParsed(
          Some(mapName), Some(startTime), _, teams, _)), Some(sha256Hash), path) =>

            currentSession.copy(lastUpdated = java.time.Instant.now(),
            uploadState = currentSession.uploadState.updateOnePendingTo(uuid => ValidGame(teams.flatMap(_.participants.map(_.name)), mapName, LocalDateTime.now(), sha256Hash, uuid)))
          case FileProcessResult(_, _, _, _, _, errorMessage, _, _, _) => currentSession.copy(lastUpdated = java.time.Instant.now(),
            uploadState = currentSession.uploadState.updateOnePendingTo(uuid => InvalidGame(errorMessage.getOrElse("Otro error"), uuid)))
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
        val r = uploadedFileRepository.findBySha256Hash(sha256)
        r.map(_.isDefined)
      case None =>
        Future.successful(false)
    }
  }

  /**
   * Remove a specific file from session by SHA256 hash
   */
  def removeFileFromSession(currentSession: UploadSession, sessionUUID: UUID): UploadSession = {
    currentSession.copy(uploadState = currentSession.uploadState.copy(games = currentSession.uploadState.games.filter{
      _.sessionID.compareTo(sessionUUID) != 0
    }))
  }

  /**
   * Get all active sessions (for debugging/monitoring)
   */
  def getAllActiveSessions: List[UploadSession] = {
    val activeSessions = sessions.asScala.values
      .filter(session => !isSessionExpired(session))
      .toList

    // Clean up expired sessions
    cleanupExpiredSessions()

    activeSessions
  }

  /**
   * Get sessions for a specific match (both users)
   */
  def getSessionsForMatch(matchId: Long): List[UploadSession] = {
    val matchSessions = sessions.asScala.values
      .filter(session => session.challongeMatchID == matchId && !isSessionExpired(session))
      .toList

    matchSessions
  }

  /**
   * Check if a session has expired
   */
  private def isSessionExpired(session: UploadSession): Boolean = {
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
}
