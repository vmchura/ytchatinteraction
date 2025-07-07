package services

import models.User

import javax.inject.*
import java.util.concurrent.ConcurrentHashMap
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import play.api.Logger

/**
 * Represents an upload session for a specific user and match
 */
case class UploadSession(
  sessionId: String,
  matchId: String,
  userId: Long,
  uploadedFiles: List[FileProcessResult],
  createdAt: Instant,
  lastUpdated: Instant,
  isFinalized: Boolean = false
) {
  def addFile(fileResult: FileProcessResult): UploadSession = {
    this.copy(
      uploadedFiles = uploadedFiles :+ fileResult,
      lastUpdated = Instant.now()
    )
  }

  def finalizeSession(): UploadSession = {
    this.copy(
      isFinalized = true,
      lastUpdated = Instant.now()
    )
  }

  def successfulFiles: List[FileProcessResult] = uploadedFiles.filter(_.success)
  def failedFiles: List[FileProcessResult] = uploadedFiles.filter(!_.success)
  def totalFiles: Int = uploadedFiles.length
  def hasFiles: Boolean = uploadedFiles.nonEmpty
}

/**
 * Key for identifying upload sessions by user and match
 */
case class SessionKey(userId: String, matchId: String) {
  override def toString: String = s"${userId}_$matchId"
}

/**
 * In-memory service for managing upload sessions
 */
@Singleton
class UploadSessionService @Inject()()(implicit ec: ExecutionContext) {
  
  private val logger = Logger(getClass)
  private val sessions = new ConcurrentHashMap[SessionKey, UploadSession]()
  
  // Session timeout in minutes
  private val sessionTimeoutMinutes = 30
  
  /**
   * Start a new upload session for a user and match
   */
  def startSession(user: User, matchId: String): Future[UploadSession] = {
    val sessionKey = SessionKey(user.userId.toString, matchId)
    val sessionId = UUID.randomUUID().toString
    
    val session = UploadSession(
      sessionId = sessionId,
      matchId = matchId,
      userId = user.userId,
      uploadedFiles = List.empty,
      createdAt = Instant.now(),
      lastUpdated = Instant.now()
    )
    
    sessions.put(sessionKey, session)
    logger.info(s"Started upload session: $sessionId for user: $user, match: $matchId")
    
    Future.successful(session)
  }
  
  /**
   * Get existing session or create a new one if it doesn't exist
   */
  def getOrCreateSession(user: User, matchId: String): Future[UploadSession] = {
    val sessionKey = SessionKey(user.userId.toString, matchId)
    
    Option(sessions.get(sessionKey)) match {
      case Some(existingSession) if !isSessionExpired(existingSession) =>
        logger.debug(s"Retrieved existing session: ${existingSession.sessionId}")
        Future.successful(existingSession)
      case Some(expiredSession) =>
        logger.info(s"Session expired: ${expiredSession.sessionId}, creating new one")
        sessions.remove(sessionKey)
        startSession(user, matchId)
      case None =>
        logger.info(s"No session found, creating new one for user: ${user.userId}, match: $matchId")
        startSession(user, matchId)
    }
  }
  
  /**
   * Add a file to an existing session
   */
  def addFileToSession(user: User, matchId: String, fileResult: FileProcessResult): Future[Option[UploadSession]] = {
    val sessionKey = SessionKey(user.userId.toString, matchId)
    
    Option(sessions.get(sessionKey)) match {
      case Some(session) if !session.isFinalized && !isSessionExpired(session) =>
        val updatedSession = session.addFile(fileResult)
        sessions.put(sessionKey, updatedSession)
        logger.info(s"Added file ${fileResult.fileName} to session: ${session.sessionId}")
        Future.successful(Some(updatedSession))
      case Some(session) if session.isFinalized =>
        logger.warn(s"Attempted to add file to finalized session: ${session.sessionId}")
        Future.successful(None)
      case Some(expiredSession) =>
        logger.warn(s"Attempted to add file to expired session: ${expiredSession.sessionId}")
        sessions.remove(sessionKey)
        Future.successful(None)
      case None =>
        logger.warn(s"No session found for user: ${user.userId}, match: $matchId")
        Future.successful(None)
    }
  }
  
  /**
   * Get current session for user and match
   */
  def getSession(user: User, matchId: String): Future[Option[UploadSession]] = {
    val sessionKey = SessionKey(user.userId.toString, matchId)
    
    Option(sessions.get(sessionKey)) match {
      case Some(session) if !isSessionExpired(session) =>
        Future.successful(Some(session))
      case Some(expiredSession) =>
        logger.info(s"Removing expired session: ${expiredSession.sessionId}")
        sessions.remove(sessionKey)
        Future.successful(None)
      case None =>
        Future.successful(None)
    }
  }
  
  /**
   * Finalize a session (no more files can be added)
   */
  def finalizeSession(user: User, matchId: String): Future[Option[UploadSession]] = {
    val sessionKey = SessionKey(user.userId.toString, matchId)
    
    Option(sessions.get(sessionKey)) match {
      case Some(session) if !isSessionExpired(session) =>
        val finalizedSession = session.finalizeSession()
        sessions.put(sessionKey, finalizedSession)
        logger.info(s"Finalized session: ${session.sessionId} with ${session.totalFiles} files")
        Future.successful(Some(finalizedSession))
      case Some(expiredSession) =>
        logger.warn(s"Attempted to finalize expired session: ${expiredSession.sessionId}")
        sessions.remove(sessionKey)
        Future.successful(None)
      case None =>
        logger.warn(s"No session found to finalize for user: ${user.userId}, match: $matchId")
        Future.successful(None)
    }
  }
  
  /**
   * Clear/delete a session
   */
  def clearSession(userId: String, matchId: String): Future[Boolean] = {
    val sessionKey = SessionKey(userId, matchId)
    val removed = Option(sessions.remove(sessionKey)).isDefined
    
    if (removed) {
      logger.info(s"Cleared session for user: $userId, match: $matchId")
    }
    
    Future.successful(removed)
  }
  
  /**
   * Get all active sessions (for debugging/monitoring)
   */
  def getAllActiveSessions: Future[List[UploadSession]] = {
    val activeSessions = sessions.asScala.values
      .filter(session => !isSessionExpired(session))
      .toList
    
    // Clean up expired sessions
    cleanupExpiredSessions()
    
    Future.successful(activeSessions)
  }
  
  /**
   * Get sessions for a specific match (both users)
   */
  def getSessionsForMatch(matchId: String): Future[List[UploadSession]] = {
    val matchSessions = sessions.asScala.values
      .filter(session => session.matchId == matchId && !isSessionExpired(session))
      .toList
    
    Future.successful(matchSessions)
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
  
  /**
   * Get session statistics
   */
  def getSessionStats: Future[Map[String, Int]] = {
    cleanupExpiredSessions()
    
    val stats = Map(
      "totalActiveSessions" -> sessions.size(),
      "finalizedSessions" -> sessions.asScala.values.count(_.isFinalized),
      "sessionsWithFiles" -> sessions.asScala.values.count(_.hasFiles)
    )
    
    Future.successful(stats)
  }
}
