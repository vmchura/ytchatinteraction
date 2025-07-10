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
                                      uploadSessionService: UploadSessionService,
                                      tournamentService: services.TournamentService,
                                      userRepository: models.repository.UserRepository
                                    )(implicit ec: ExecutionContext) extends SilhouetteController(scc) {


  /**
   * Show the file upload form for a specific match
   */
  def uploadFormForMatch(matchId: String): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    for {
      currentSessionOpt <- uploadSessionService.getSession(request.identity, matchId)
      currentSession <- currentSessionOpt.fold(uploadSessionService.startSession(request.identity, matchId))(session => Future.successful(session))
      
      // Get match details
      matchOpt <- tournamentService.getMatch(matchId)
      
      // Get tournament and user information if match exists
      matchDetailsOpt <- matchOpt match {
        case Some(tournamentMatch) =>
          for {
            tournamentOpt <- tournamentService.getTournament(tournamentMatch.tournamentId.toLong)
            firstUserOpt <- userRepository.getById(tournamentMatch.firstUserId)
            secondUserOpt <- userRepository.getById(tournamentMatch.secondUserId)
          } yield Some((tournamentMatch, tournamentOpt, firstUserOpt, secondUserOpt))
        case None => Future.successful(None)
      }
      
    } yield {
      matchDetailsOpt match {
        case Some((tournamentMatch, tournamentOpt, firstUserOpt, secondUserOpt)) =>
          Ok(views.html.fileUpload(
            request.identity, 
            matchId, 
            currentSession, 
            None, 
            tournamentOpt, 
            Some(tournamentMatch), 
            firstUserOpt, 
            secondUserOpt
          ))
        case None =>
          Ok(views.html.fileUpload(
            request.identity, 
            matchId, 
            currentSession, 
            Some("Match not found"), 
            None, 
            None, 
            None, 
            None
          ))
      }
    }
  }

  /**
   * Handle multiple file upload and processing - returns HTML view (legacy endpoint)
   */
  def uploadFile(matchId: String): Action[MultipartFormData[TemporaryFile]] = silhouette.SecuredAction.async(parse.multipartFormData) { implicit request =>
    uploadSessionService.getOrCreateSession(request.identity, matchId).flatMap { session =>
      if (session.isFinalized) {
        renderUploadFormWithMatchDetails(request.identity, matchId, session)
      } else {
        val uploadedFiles = request.body.files.filter(_.key == "upload_file")
        if (uploadedFiles.isEmpty) {
          renderUploadFormWithMatchDetails(request.identity, matchId, session)
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
   * Helper method to render upload form with match details
   */
  private def renderUploadFormWithMatchDetails(
    user: User,
    matchId: String,
    session: UploadSession,
    errorMessage: Option[String] = None
  )(implicit request: RequestHeader): Future[Result] = {
    tournamentService.getMatch(matchId).flatMap { matchOpt =>
      matchOpt match {
        case Some(tournamentMatch) =>
          for {
            tournamentOpt <- tournamentService.getTournament(tournamentMatch.tournamentId.toLong)
            firstUserOpt <- userRepository.getById(tournamentMatch.firstUserId)
            secondUserOpt <- userRepository.getById(tournamentMatch.secondUserId)
          } yield Ok(views.html.fileUpload(
            user,
            matchId,
            session,
            errorMessage,
            tournamentOpt,
            Some(tournamentMatch),
            firstUserOpt,
            secondUserOpt
          ))
        case None =>
          Future.successful(Ok(views.html.fileUpload(
            user,
            matchId,
            session,
            errorMessage.orElse(Some("Match not found")),
            None,
            None,
            None,
            None
          )))
      }
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
        // Get the updated session and render with match details
        uploadSessionService.getSession(user, matchId).flatMap { sessionOpt =>
          sessionOpt match {
            case Some(session) =>
              renderUploadFormWithMatchDetails(user, matchId, session)
            case None =>
              Future.successful(Ok(views.html.index(Some(user))))
          }
        }
      }
    }
  }
}
