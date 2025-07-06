package controllers

import javax.inject.*
import play.api.Logger
import play.api.mvc.*
import play.api.libs.json.{Json, JsValue}
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.WSClient
import play.api.libs.ws.JsonBodyWritables.*
import play.api.libs.ws.DefaultBodyReadables.readableAsString
import play.api.Configuration
import scala.concurrent.{ExecutionContext, Future}
import java.nio.file.Files
import java.util.Base64
import models.StarCraftModels.GameInfo

case class FileUploadState(
                            message: String,
                            uploadType: String
                          )

case class FileProcessResult(
                              fileName: String,
                              originalSize: Long,
                              contentType: String,
                              processedAt: String,
                              success: Boolean,
                              errorMessage: Option[String] = None,
                              base64Preview: Option[String] = None,
                              gameInfo: Option[GameInfo] = None
                            )

case class MultiFileUploadResult(
                                  totalFiles: Int,
                                  successfulFiles: List[FileProcessResult],
                                  failedFiles: List[FileProcessResult],
                                  totalSuccessful: Int,
                                  totalFailed: Int
                                )

sealed trait FileUploadError

case object FileIsNotOne extends FileUploadError

case object FileSelectedNotSmallSize extends FileUploadError

case object FileSelectedWrongType extends FileUploadError

case class FileErrorReceivingParse(error: String) extends FileUploadError

case class ErrorByServerParsing(error: String) extends FileUploadError

sealed trait FileProcessState

case object FileOnProcessToParse extends FileProcessState

case object MatchingUsers extends FileProcessState

case class FileUploadComplete(result: String) extends FileProcessState

object FileUploadState {
  implicit val fileUploadStateWrites: play.api.libs.json.OWrites[FileUploadState] = Json.writes[FileUploadState]
}

/**
 * Controller for handling file uploads similar to replay parsing functionality
 */
@Singleton
class FileUploadController @Inject()(
                                      val scc: SilhouetteControllerComponents,
                                      wsClient: WSClient,
                                      configuration: Configuration
                                    )(implicit ec: ExecutionContext) extends SilhouetteController(scc) {

  private val logger = Logger(getClass)
  private val replayParserUrl = configuration.get[String]("replayparser.url")

  /**
   * Show the file upload form
   */
  def uploadForm(): Action[AnyContent] = silhouette.UserAwareAction { implicit request =>
    Ok(views.html.fileUpload(request.identity))
  }

  /**
   * Handle multiple file upload and processing - returns HTML view
   */
  def uploadFile(): Action[MultipartFormData[TemporaryFile]] = silhouette.UserAwareAction.async(parse.multipartFormData) { implicit request =>

    val uploadedFiles = request.body.files.filter(_.key == "upload_file")

    if (uploadedFiles.isEmpty) {
      Future.successful(
        BadRequest(views.html.fileUpload(
          request.identity,
          Some(MultiFileUploadResult(
            totalFiles = 0,
            successfulFiles = List.empty,
            failedFiles = List.empty,
            totalSuccessful = 0,
            totalFailed = 0
          )),
          Some("No files were uploaded")
        ))
      )
    } else {
      processMultipleFiles(uploadedFiles).map { result =>
        Ok(views.html.fileUpload(request.identity, Some(result)))
      }
    }
  }

  /**
   * Process multiple files and return comprehensive results
   */
  private def processMultipleFiles(files: Seq[MultipartFormData.FilePart[TemporaryFile]]): Future[MultiFileUploadResult] = {
    val futureResults = files.map { file =>
      validateAndProcessSingleFile(file)
    }.toList

    Future.sequence(futureResults).map { results =>
      val successful = results.filter(_.success)
      val failed = results.filter(!_.success)

      MultiFileUploadResult(
        totalFiles = files.length,
        successfulFiles = successful,
        failedFiles = failed,
        totalSuccessful = successful.length,
        totalFailed = failed.length
      )
    }
  }

  /**
   * Process a single file and return result
   */
  private def validateAndProcessSingleFile(file: MultipartFormData.FilePart[TemporaryFile]): Future[FileProcessResult] = {
    val validation = for {
      _ <- validateFileSize(file)
      _ <- validateFileType(file)
    } yield file

    validation match {
      case Right(validFile) =>
        processFileToResult(validFile)
      case Left(error) =>
        Future.successful(FileProcessResult(
          fileName = file.filename,
          originalSize = tryGetFileSize(file),
          contentType = file.contentType.getOrElse("unknown"),
          processedAt = java.time.Instant.now().toString,
          success = false,
          errorMessage = Some(getErrorMessage(error)),
          gameInfo = None
        ))
    }
  }

  private def tryGetFileSize(file: MultipartFormData.FilePart[TemporaryFile]): Long = {
    try {
      Files.size(file.ref.path)
    } catch {
      case _: Exception => 0L
    }
  }

  private def validateFileSize(file: MultipartFormData.FilePart[TemporaryFile]): Either[FileUploadError, MultipartFormData.FilePart[TemporaryFile]] = {
    val maxSizeBytes = 1 * 1024 * 1024 // 1MB limit
    val fileSize = tryGetFileSize(file)

    if (fileSize > maxSizeBytes) {
      Left(FileSelectedNotSmallSize)
    } else {
      Right(file)
    }
  }

  private def validateFileType(file: MultipartFormData.FilePart[TemporaryFile]): Either[FileUploadError, MultipartFormData.FilePart[TemporaryFile]] = {
    val allowedExtensions = Seq(".rep") // Add your allowed extensions
    val fileName = file.filename

    if (allowedExtensions.exists(ext => fileName.toLowerCase.endsWith(ext))) {
      Right(file)
    } else {
      Left(FileSelectedWrongType)
    }
  }

  private def processFileToResult(file: MultipartFormData.FilePart[TemporaryFile]): Future[FileProcessResult] = {
    try {
      val fileBytes = Files.readAllBytes(file.ref.path)
      val base64Content = Base64.getEncoder.encodeToString(fileBytes)
      val fileName = file.filename

      logger.info(s"Processing file: $fileName, size: ${fileBytes.length} bytes")

      // Prepare request payload for the Go replay parser service
      val requestPayload = Json.obj(
        "replayfile" -> base64Content,
        "filename" -> fileName
      )

      // Call the replay parser service
      wsClient.url(s"$replayParserUrl/parse-replay")
        .withHttpHeaders("Content-Type" -> "application/json")
        .post(requestPayload)
        .map { response =>
          if (response.status == 200) {
            logger.info(s"Successfully parsed replay file: $fileName")
            val responseBody = response.body[String]
            
            // Parse GameInfo from the JSON response
            val gameInfo = try {
              Some(GameInfo.parseFromJson(responseBody))
            } catch {
              case ex: Exception =>
                logger.warn(s"Failed to parse GameInfo from response for file $fileName: ${ex.getMessage}")
                None
            }
            
            FileProcessResult(
              fileName = fileName,
              originalSize = fileBytes.length,
              contentType = file.contentType.getOrElse("unknown"),
              processedAt = java.time.Instant.now().toString,
              success = true,
              errorMessage = None,
              base64Preview = Some(responseBody.take(500)), // First 500 chars of the parsed result
              gameInfo = gameInfo
            )
          } else {
            logger.error(s"Replay parser service returned error ${response.status}: ${response.body[String]}")
            FileProcessResult(
              fileName = fileName,
              originalSize = fileBytes.length,
              contentType = file.contentType.getOrElse("unknown"),
              processedAt = java.time.Instant.now().toString,
              success = false,
              errorMessage = Some(s"Parser service error (${response.status}): ${response.body[String]}"),
              gameInfo = None
            )
          }
        }
        .recover { case ex: Exception =>
          logger.error(s"Error calling replay parser service for file $fileName: ${ex.getMessage}", ex)
          FileProcessResult(
            fileName = fileName,
            originalSize = fileBytes.length,
            contentType = file.contentType.getOrElse("unknown"),
            processedAt = java.time.Instant.now().toString,
            success = false,
            errorMessage = Some(s"Service call failed: ${ex.getMessage}"),
            gameInfo = None
          )
        }

    } catch {
      case ex: Exception =>
        logger.error(s"Error reading file ${file.filename}: ${ex.getMessage}", ex)
        Future.successful(FileProcessResult(
          fileName = file.filename,
          originalSize = tryGetFileSize(file),
          contentType = file.contentType.getOrElse("unknown"),
          processedAt = java.time.Instant.now().toString,
          success = false,
          errorMessage = Some(s"File reading error: ${ex.getMessage}"),
          gameInfo = None
        ))
    }
  }

  private def getErrorMessage(error: FileUploadError): String = error match {
    case FileIsNotOne => "Please select exactly one file"
    case FileSelectedNotSmallSize => "File size must be less than 1MB"
    case FileSelectedWrongType => "File type not supported. Allowed types: .rep"
    case FileErrorReceivingParse(message) => s"Error processing file: $message"
    case ErrorByServerParsing(message) => s"Server error: $message"
  }

  /**
   * API endpoint to get upload status (kept for compatibility)
   */
  def uploadStatus(): Action[AnyContent] = silhouette.UserAwareAction { implicit request =>
    // This could be enhanced to track upload progress in a real implementation
    Ok(Json.obj(
      "status" -> "ready",
      "maxFileSize" -> "1MB",
      "allowedTypes" -> Json.arr(".rep"),
      "multipleFilesSupported" -> true
    ))
  }
}
