package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.Json
import play.api.libs.Files.TemporaryFile

import scala.concurrent.{ExecutionContext, Future}
import services.{MultiFileUploadResult, ParseReplayFileService, UploadSession, UploadSessionService}
import models.{TournamentMatch, User}
import play.silhouette.api.actions.SecuredRequest

case class FileUploadState(
                            message: String,
                            uploadType: String
                          )

object FileUploadState {
  implicit val fileUploadStateWrites: play.api.libs.json.OWrites[FileUploadState] = Json.writes[FileUploadState]
}

/**
 * Controller for handling file uploads with session management for tournament matches
 */
@Singleton
class FileUploadController @Inject()(
                                      val scc: SilhouetteControllerComponents,
                                      parseReplayFileService: ParseReplayFileService,
                                      uploadSessionService: UploadSessionService
                                    )(implicit ec: ExecutionContext) extends SilhouetteController(scc) {


  /**
   * Show the file upload form for a specific match
   */
  def uploadFormForMatch(matchId: String): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    for {
      currentSessionOpt <- uploadSessionService.getSession(request.identity, matchId)
      currentSession <- currentSessionOpt.fold(uploadSessionService.startSession(request.identity, matchId))(session => Future.successful(session))
    }yield{
      Ok(views.html.fileUpload(request.identity, matchId, currentSession))
    }
  }

  /**
   * Handle multiple file upload and processing - returns HTML view (legacy endpoint)
   */
  def uploadFile(matchId: String): Action[MultipartFormData[TemporaryFile]] = silhouette.SecuredAction.async(parse.multipartFormData) { implicit request =>
    uploadSessionService.getOrCreateSession(request.identity, matchId).flatMap { session =>
      if (session.isFinalized) {
        Future.successful(Ok(views.html.fileUpload(request.identity, matchId, session)))
      } else {
        val uploadedFiles = request.body.files.filter(_.key == "upload_file")
        if (uploadedFiles.isEmpty) {
          Future.successful(Ok(views.html.fileUpload(request.identity, matchId, session)))
        } else {
          processFilesAndAddToSession(request.identity, matchId, uploadedFiles)(request)
        }
      }
    }
  }
  


  /**
   * Finalize an upload session (no more files can be added)
   */
  def finalizeSession(matchId: String): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    val user = request.identity
    
    uploadSessionService.finalizeSession(user, matchId).map {
      case Some(session) =>
        Ok(views.html.index(Some(user)))
      case None =>
        Ok(views.html.index(Some(user)))
    }
  }


  /**
   * Helper method to process files and add them to session
   */
  private def processFilesAndAddToSession(
    user: User,
    matchId: String, 
    uploadedFiles: Seq[MultipartFormData.FilePart[TemporaryFile]]
  )(implicit request: SecuredRequest[EnvType, MultipartFormData[TemporaryFile]]): Future[Result] = {
    
    // Process all files first
    val futureResults = uploadedFiles.map { file =>
      parseReplayFileService.validateAndProcessSingleFile(file)
    }.toList

    Future.sequence(futureResults).flatMap { fileResults =>
      // Add each result to the session
      val addFutures = fileResults.map { fileResult =>
        uploadSessionService.addFileToSession(user, matchId, fileResult)
      }

      Future.sequence(addFutures).flatMap { sessionResults =>
        // Get the updated session
        uploadSessionService.getSession(user, matchId).map { sessionOpt =>
          sessionOpt match {
            case Some(session) =>
              Ok(views.html.fileUpload(user, matchId, session))
            case None =>
              Ok(views.html.index(Some(user)))
          }
        }
      }
    }
  }
}
