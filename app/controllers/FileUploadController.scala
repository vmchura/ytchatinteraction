package controllers

import javax.inject.*
import play.api.Logger
import play.api.mvc.*
import play.api.libs.json.{Json, JsValue}
import play.api.libs.Files.TemporaryFile
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}
import java.nio.file.Files
import java.util.Base64

case class FileUploadState(
  message: String,
  uploadType: String
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
  val scc: SilhouetteControllerComponents
)(implicit ec: ExecutionContext) extends SilhouetteController(scc) {

  private val logger = Logger(getClass)
  
  /**
   * Show the file upload form
   */
  def uploadForm(): Action[AnyContent] = silhouette.UserAwareAction { implicit request =>
    Ok(views.html.fileUpload(request.identity))
  }

  /**
   * Handle file upload and processing
   */
  def uploadFile(): Action[MultipartFormData[TemporaryFile]] = silhouette.UserAwareAction.async(parse.multipartFormData) { implicit request =>
    
    request.body.file("upload_file") match {
      case Some(file) =>
        validateAndProcessFile(file).map { result =>
          result match {
            case Right(successData) =>
              Ok(Json.obj(
                "status" -> "success",
                "message" -> "File uploaded and processed successfully",
                "data" -> successData
              ))
            case Left(error) =>
              BadRequest(Json.obj(
                "status" -> "error", 
                "message" -> getErrorMessage(error)
              ))
          }
        }
        
      case None =>
        Future.successful(BadRequest(Json.obj(
          "status" -> "error",
          "message" -> "No file provided"
        )))
    }
  }

  private def validateAndProcessFile(file: MultipartFormData.FilePart[TemporaryFile]): Future[Either[FileUploadError, JsValue]] = {
    Future {
      val validation = for {
        validatedFile <- validateFileCount(file)
        _ <- validateFileSize(validatedFile)
        _ <- validateFileType(validatedFile)
      } yield validatedFile

      validation match {
        case Right(validFile) =>
          processFile(validFile)
        case Left(error) =>
          Left(error)
      }
    }
  }

  private def validateFileCount(file: MultipartFormData.FilePart[TemporaryFile]): Either[FileUploadError, MultipartFormData.FilePart[TemporaryFile]] = {
    // In this context, we always have exactly one file, so this always succeeds
    Right(file)
  }

  private def validateFileSize(file: MultipartFormData.FilePart[TemporaryFile]): Either[FileUploadError, MultipartFormData.FilePart[TemporaryFile]] = {
    val maxSizeBytes = 1 * 1024 * 1024 // 1MB limit
    val fileSize = Files.size(file.ref.path)
    
    if (fileSize > maxSizeBytes) {
      Left(FileSelectedNotSmallSize)
    } else {
      Right(file)
    }
  }

  private def validateFileType(file: MultipartFormData.FilePart[TemporaryFile]): Either[FileUploadError, MultipartFormData.FilePart[TemporaryFile]] = {
    val allowedExtensions = Seq(".rep", ".txt", ".json", ".csv") // Add your allowed extensions
    val fileName = file.filename
    
    if (allowedExtensions.exists(ext => fileName.toLowerCase.endsWith(ext))) {
      Right(file)
    } else {
      Left(FileSelectedWrongType)
    }
  }

  private def processFile(file: MultipartFormData.FilePart[TemporaryFile]): Either[FileUploadError, JsValue] = {
    try {
      val fileBytes = Files.readAllBytes(file.ref.path)
      val base64Content = Base64.getEncoder.encodeToString(fileBytes)
      val fileName = file.filename
      
      logger.info(s"Processing file: $fileName, size: ${fileBytes.length} bytes")
      
      // Here you would integrate with your actual file processing logic
      // For now, we'll return a success response with file metadata
      val result = Json.obj(
        "filename" -> fileName,
        "size" -> fileBytes.length,
        "contentType" -> file.contentType.getOrElse("unknown"),
        "processedAt" -> java.time.Instant.now().toString,
        "base64Preview" -> base64Content.take(100) // First 100 chars for preview
      )
      
      Right(result)
      
    } catch {
      case ex: Exception =>
        logger.error(s"Error processing file: ${ex.getMessage}", ex)
        Left(FileErrorReceivingParse(ex.getMessage))
    }
  }

  private def getErrorMessage(error: FileUploadError): String = error match {
    case FileIsNotOne => "Please select exactly one file"
    case FileSelectedNotSmallSize => "File size must be less than 1MB"
    case FileSelectedWrongType => "File type not supported. Allowed types: .rep, .txt, .json, .csv"
    case FileErrorReceivingParse(message) => s"Error processing file: $message"
    case ErrorByServerParsing(message) => s"Server error: $message"
  }

  /**
   * API endpoint to get upload status
   */
  def uploadStatus(): Action[AnyContent] = silhouette.UserAwareAction { implicit request =>
    // This could be enhanced to track upload progress in a real implementation
    Ok(Json.obj(
      "status" -> "ready",
      "maxFileSize" -> "1MB",
      "allowedTypes" -> Json.arr(".rep", ".txt", ".json", ".csv")
    ))
  }
}
