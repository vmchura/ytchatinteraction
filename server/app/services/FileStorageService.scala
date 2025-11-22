package services

import javax.inject.*
import play.api.Logger
import play.api.Configuration
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.time.Instant
import java.util.UUID
import scala.util.{Try, Success, Failure}

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
                         )

case class AnalyticalFileInfo(
                               originalFileName: String,
                               storedFileName: String,
                               storedPath: String,
                               size: Long,
                               contentType: String,
                               storedAt: Instant,
                               userId: Long
                             )

/**
 * Service for managing file storage operations
 */
trait FileStorageService {
  def storeFile(
                 fileBytes: Array[Byte],
                 originalFileName: String,
                 contentType: String,
                 userId: Long,
                 matchId: Long,
                 sessionId: String
               ): Future[Either[String, StoredFileInfo]]

  def storeAnalyticalFile(
                           fileBytes: Array[Byte],
                           originalFileName: String,
                           contentType: String,
                           userId: Long,
                         ): Future[Either[String, AnalyticalFileInfo]]

  def deleteFile(storedPath: String): Future[Boolean]

  def fileExists(storedPath: String): Future[Boolean]

  def getStorageStats: Future[Map[String, Any]]
}

@Singleton
class DefaultFileStorageService @Inject()(
                                           configuration: Configuration
                                         )(implicit ec: ExecutionContext) extends FileStorageService {

  private val logger = Logger(getClass)

  // Storage path for file uploads (mounted via Dokku in production, local in development)
  private val uploadStoragePath = configuration.get[String]("app.storage.uploads.path")

  override def storeFile(
                          fileBytes: Array[Byte],
                          originalFileName: String,
                          contentType: String,
                          userId: Long,
                          matchId: Long,
                          sessionId: String
                        ): Future[Either[String, StoredFileInfo]] = Future {

    try {
      // Ensure storage directory exists
      val storageDir = Paths.get(uploadStoragePath)
      if (!Files.exists(storageDir)) {
        Files.createDirectories(storageDir)
        logger.info(s"Created storage directory: $uploadStoragePath")
      }

      // Generate unique filename to avoid conflicts
      val fileExtension = getFileExtension(originalFileName)
      val timestamp = Instant.now().getEpochSecond
      val uniqueId = UUID.randomUUID().toString.substring(0, 8)
      val storedFileName = s"${userId}_${matchId}_${timestamp}_${uniqueId}${fileExtension}"

      // Create the full path
      val storedPath = storageDir.resolve(storedFileName)

      // Write the file
      Files.write(storedPath, fileBytes)

      val storedFileInfo = StoredFileInfo(
        originalFileName = originalFileName,
        storedFileName = storedFileName,
        storedPath = storedPath.toString,
        size = fileBytes.length,
        contentType = contentType,
        storedAt = Instant.now(),
        userId = userId,
        matchId = matchId,
        sessionId = sessionId
      )

      logger.info(s"Successfully stored file: $originalFileName as $storedFileName for user $userId, match $matchId")
      Right(storedFileInfo)

    } catch {
      case ex: Exception =>
        logger.error(s"Failed to store file $originalFileName for user $userId, match $matchId: ${ex.getMessage}", ex)
        Left(s"Failed to store file: ${ex.getMessage}")
    }
  }

  override def storeAnalyticalFile(
                                    fileBytes: Array[Byte],
                                    originalFileName: String,
                                    contentType: String,
                                    userId: Long
                                  ): Future[Either[String, AnalyticalFileInfo]] = Future {

    try {
      // Ensure storage directory exists
      val storageDir = Paths.get(uploadStoragePath)
      if (!Files.exists(storageDir)) {
        Files.createDirectories(storageDir)
        logger.info(s"Created storage directory: $uploadStoragePath")
      }

      // Generate unique filename to avoid conflicts
      val fileExtension = getFileExtension(originalFileName)
      val timestamp = Instant.now().getEpochSecond
      val uniqueId = UUID.randomUUID().toString.substring(0, 8)
      val storedFileName = s"${userId}_${timestamp}_$uniqueId$fileExtension"

      // Create the full path
      val storedPath = storageDir.resolve(storedFileName)

      // Write the file
      Files.write(storedPath, fileBytes)

      val storedFileInfo = AnalyticalFileInfo(
        originalFileName = originalFileName,
        storedFileName = storedFileName,
        storedPath = storedPath.toString,
        size = fileBytes.length,
        contentType = contentType,
        storedAt = Instant.now(),
        userId = userId
      )

      logger.info(s"Successfully stored file: $originalFileName as $storedFileName for user $userId")
      Right(storedFileInfo)

    } catch {
      case ex: Exception =>
        logger.error(s"Failed to store file $originalFileName for user $userId, ${ex.getMessage}", ex)
        Left(s"Failed to store file: ${ex.getMessage}")
    }
  }

  override def deleteFile(storedPath: String): Future[Boolean] = Future {
    try {
      val path = Paths.get(storedPath)
      val deleted = Files.deleteIfExists(path)
      if (deleted) {
        logger.info(s"Deleted file: $storedPath")
      } else {
        logger.warn(s"File not found for deletion: $storedPath")
      }
      deleted
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to delete file $storedPath: ${ex.getMessage}", ex)
        false
    }
  }

  override def fileExists(storedPath: String): Future[Boolean] = Future {
    try {
      Files.exists(Paths.get(storedPath))
    } catch {
      case _: Exception => false
    }
  }

  override def getStorageStats: Future[Map[String, Any]] = Future {
    try {
      val storageDir = Paths.get(uploadStoragePath)

      if (!Files.exists(storageDir)) {
        Map(
          "directoryExists" -> false,
          "error" -> "Storage directory does not exist"
        )
      } else {
        val files = Files.list(storageDir)
        val fileList = files.iterator().asScala.toList
        files.close()

        val totalFiles = fileList.length
        val totalSize = fileList.map(path => Try(Files.size(path)).getOrElse(0L)).sum

        val (freeSpace, totalSpace) = Try {
          val store = Files.getFileStore(storageDir)
          (store.getUsableSpace, store.getTotalSpace)
        }.getOrElse((0L, 0L))

        Map(
          "directoryExists" -> true,
          "totalFiles" -> totalFiles,
          "totalSizeBytes" -> totalSize,
          "totalSizeMB" -> (totalSize / (1024 * 1024)).toDouble,
          "freeSpaceGB" -> (freeSpace / (1024 * 1024 * 1024)).toDouble,
          "totalSpaceGB" -> (totalSpace / (1024 * 1024 * 1024)).toDouble,
          "path" -> uploadStoragePath
        )
      }
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to get storage stats: ${ex.getMessage}", ex)
        Map(
          "error" -> ex.getMessage
        )
    }
  }

  private def getFileExtension(fileName: String): String = {
    val dotIndex = fileName.lastIndexOf('.')
    if (dotIndex > 0 && dotIndex < fileName.length - 1) {
      fileName.substring(dotIndex)
    } else {
      ""
    }
  }
}
