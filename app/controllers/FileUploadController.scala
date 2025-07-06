package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.Json
import play.api.libs.Files.TemporaryFile
import scala.concurrent.{ExecutionContext, Future}
import services.{ParseReplayFileService, MultiFileUploadResult}

case class FileUploadState(
                            message: String,
                            uploadType: String
                          )

object FileUploadState {
  implicit val fileUploadStateWrites: play.api.libs.json.OWrites[FileUploadState] = Json.writes[FileUploadState]
}

/**
 * Controller for handling file uploads similar to replay parsing functionality
 */
@Singleton
class FileUploadController @Inject()(
                                      val scc: SilhouetteControllerComponents,
                                      parseReplayFileService: ParseReplayFileService
                                    )(implicit ec: ExecutionContext) extends SilhouetteController(scc) {

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
      parseReplayFileService.processMultipleFiles(uploadedFiles).map { result =>
        Ok(views.html.fileUpload(request.identity, Some(result)))
      }
    }
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
