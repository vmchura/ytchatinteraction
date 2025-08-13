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
  matchId: Long,
  userId: Long,
  uploadedFiles: List[FileProcessResult],
  createdAt: Instant,
  lastUpdated: Instant,
  isFinalized: Boolean = false
) {
  def addFile(fileResult: FileProcessResult): UploadSession = {
    fileResult.sha256Hash match {
      case Some(newHash) =>
        // Check if this SHA256 already exists in the session
        val isDuplicate = uploadedFiles.exists(existing =>
          existing.sha256Hash.contains(newHash)
        )

        if (isDuplicate) {
          // For duplicate files, don't add them but update lastUpdated
          this.copy(lastUpdated = Instant.now())
        } else {
          // Add the unique file
          this.copy(
            uploadedFiles = uploadedFiles :+ fileResult,
            lastUpdated = Instant.now()
          )
        }
      case None =>
        // If no SHA256 available, add the file (fallback behavior)
        this
    }
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
case class SessionKey(userId: Long, matchId: Long) {
  override def toString: String = s"${userId}_$matchId"
}

/**
 * In-memory service for managing upload sessions
 */
@Singleton
class UploadSessionService @Inject()(
  uploadedFileRepository: models.repository.UploadedFileRepository
)(implicit ec: ExecutionContext) {
  
  private val logger = Logger(getClass)
  private val sessions = new ConcurrentHashMap[SessionKey, UploadSession]()
  
  // Session timeout in minutes
  private val sessionTimeoutMinutes = 30
  
  /**
   * Start a new upload session for a user and match
   */
  def startSession(user: User, matchId: Long): UploadSession = {
    val sessionKey = SessionKey(user.userId, matchId)
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
    
    session
  }
  
  /**
   * Get existing session or create a new one if it doesn't exist
   */
  def getOrCreateSession(user: User, matchId: Long): UploadSession = {
    val sessionKey = SessionKey(user.userId, matchId)
    
    Option(sessions.get(sessionKey)) match {
      case Some(existingSession) if !isSessionExpired(existingSession) =>
        logger.debug(s"Retrieved existing session: ${existingSession.sessionId}")
        existingSession
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
   * Add a file to an existing session with duplicate detection (both local and global)
   */
  def addFileToSession(user: User, matchId: Long, fileResult: FileProcessResult): Future[Option[UploadSession]] = {
    val sessionKey = SessionKey(user.userId, matchId)
    
    Option(sessions.get(sessionKey)) match {
      case Some(session) if !session.isFinalized && !isSessionExpired(session) =>
        // Check for duplicates before adding
        checkForDuplicateFile(fileResult).map { isDuplicate =>
          if (isDuplicate) {
            logger.info(s"File ${fileResult.fileName} with SHA256 ${fileResult.sha256Hash.getOrElse("unknown")} already exists globally, skipping")
            val updatedSession = session.copy(lastUpdated = java.time.Instant.now())
            sessions.put(sessionKey, updatedSession)
            Some(updatedSession)
          } else {
            val updatedSession = session.addFile(fileResult)
            sessions.put(sessionKey, updatedSession)

            // Log based on whether file was actually added or was a local duplicate
            val wasAdded = updatedSession.uploadedFiles.length > session.uploadedFiles.length
            if (wasAdded) {
              logger.info(s"Added file ${fileResult.fileName} to session: ${session.sessionId}")
            } else {
              logger.debug(s"File ${fileResult.fileName} already exists in session: ${session.sessionId} (local duplicate)")
            }

            Some(updatedSession)
          }
        }
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
   * Get current session for user and match
   */
  def getSession(user: User, matchId: Long): Option[UploadSession] = {
    val sessionKey = SessionKey(user.userId, matchId)
    
    Option(sessions.get(sessionKey)) match {
      case Some(session) if !isSessionExpired(session) =>
        Some(session)
      case Some(expiredSession) =>
        logger.info(s"Removing expired session: ${expiredSession.sessionId}")
        sessions.remove(sessionKey)
        None
      case None =>
        None
    }
  }
  
  /**
   * Finalize a session (no more files can be added)
   */
  def finalizeSession(user: User, matchId: Long): Option[UploadSession] = {
    val sessionKey = SessionKey(user.userId, matchId)
    
    Option(sessions.get(sessionKey)) match {
      case Some(session) if !isSessionExpired(session) =>
        val finalizedSession = session.finalizeSession()
        sessions.put(sessionKey, finalizedSession)
        logger.info(s"Finalized session: ${session.sessionId} with ${session.totalFiles} files")
        Some(finalizedSession)
      case Some(expiredSession) =>
        logger.warn(s"Attempted to finalize expired session: ${expiredSession.sessionId}")
        sessions.remove(sessionKey)
        None
      case None =>
        logger.warn(s"No session found to finalize for user: ${user.userId}, match: $matchId")
        None
    }
  }
  
  /**
   * Remove a specific file from session by SHA256 hash
   */
  def removeFileFromSession(user: User, matchId: Long, sha256Hash: String): Option[UploadSession] = {
    val sessionKey = SessionKey(user.userId, matchId)
    
    Option(sessions.get(sessionKey)) match {
      case Some(session) if !session.isFinalized && !isSessionExpired(session) =>
        val updatedFiles = session.uploadedFiles.filterNot(_.sha256Hash.contains(sha256Hash))
        val updatedSession = session.copy(
          uploadedFiles = updatedFiles,
          lastUpdated = Instant.now()
        )
        
        sessions.put(sessionKey, updatedSession)
        logger.info(s"Removed file with hash $sha256Hash from session: ${session.sessionId}")
        Some(updatedSession)
        
      case Some(session) if session.isFinalized =>
        logger.warn(s"Attempted to remove file from finalized session: ${session.sessionId}")
        None
      case Some(expiredSession) =>
        logger.warn(s"Attempted to remove file from expired session: ${expiredSession.sessionId}")
        sessions.remove(sessionKey)
        None
      case None =>
        logger.warn(s"No session found for user: ${user.userId}, match: $matchId")
        None
    }
  }

  /**
   * Clear/delete a session
   */
  def clearSession(userId: Long, matchId: Long): Boolean = {
    val sessionKey = SessionKey(userId, matchId)
    val removed = Option(sessions.remove(sessionKey)).isDefined
    
    if (removed) {
      logger.info(s"Cleared session for user: $userId, match: $matchId")
    }
    
    removed
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
      .filter(session => session.matchId == matchId && !isSessionExpired(session))
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
  
  /**
   * Get session statistics
   */
  def getSessionStats: Map[String, Int] = {
    cleanupExpiredSessions()
    
    val stats = Map(
      "totalActiveSessions" -> sessions.size(),
      "finalizedSessions" -> sessions.asScala.values.count(_.isFinalized),
      "sessionsWithFiles" -> sessions.asScala.values.count(_.hasFiles)
    )
    
    stats
  }
}
