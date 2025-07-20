package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.Json
import play.api.libs.Files.TemporaryFile

import scala.concurrent.{ExecutionContext, Future}
import services.{ParseReplayFileService, UploadSession, UploadSessionService}
import models.{TournamentMatch, User}
import play.silhouette.api.actions.SecuredRequest
import utils.auth.WithAdmin

case class FileUploadState(
                            message: String,
                            uploadType: String
                          )

object FileUploadState {
  implicit val fileUploadStateWrites: play.api.libs.json.OWrites[FileUploadState] = Json.writes[FileUploadState]
}

@Singleton
class FileUploadController @Inject()(
                                      components: DefaultSilhouetteControllerComponents,
                                      parseReplayFileService: ParseReplayFileService,
                                      uploadSessionService: UploadSessionService,
                                      tournamentService: services.TournamentService,
                                      userRepository: models.repository.UserRepository
                                    )(implicit ec: ExecutionContext) extends SilhouetteController(components) {

  def uploadFormForMatch(tournamentId: Long, matchId: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    for {
      currentSessionOpt <- uploadSessionService.getSession(request.identity, matchId)
      currentSession <- currentSessionOpt.fold(uploadSessionService.startSession(request.identity, matchId))(session => Future.successful(session))

      matchOpt <- tournamentService.getMatch(tournamentId, matchId)
      matchDetailsOpt <- matchOpt match {
        case Some(tournamentMatch) =>
          for {
            tournamentOpt <- tournamentService.getTournament(tournamentMatch.tournamentId)
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
            tournamentId,
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
            tournamentId,
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

  def uploadFile(tournamentId: Long, matchId: Long): Action[MultipartFormData[TemporaryFile]] = silhouette.SecuredAction(WithAdmin()).async(parse.multipartFormData) { implicit request =>
    uploadSessionService.getOrCreateSession(request.identity, matchId).flatMap { session =>
      if (session.isFinalized) {
        renderUploadFormWithMatchDetails(request.identity, tournamentId, matchId, session)
      } else {
        val uploadedFiles = request.body.files.filter(_.key == "upload_file")
        if (uploadedFiles.isEmpty) {
          renderUploadFormWithMatchDetails(request.identity, tournamentId, matchId, session)
        } else {
          processFilesAndAddToSession(request.identity, tournamentId, matchId, uploadedFiles)(request)
        }
      }
    }
  }

  def finalizeSession(tournamentId: Long, matchId: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    val user = request.identity

    uploadSessionService.finalizeSession(user, matchId).map {
      case Some(session) =>
        Ok(views.html.index(Some(user)))
      case None =>
        Ok(views.html.index(Some(user)))
    }
  }


  private def renderUploadFormWithMatchDetails(
                                                user: User,
                                                tournamentId: Long,
                                                matchId: Long,
                                                session: UploadSession,
                                                errorMessage: Option[String] = None
                                              )(implicit request: RequestHeader): Future[Result] = {
    tournamentService.getMatch(tournamentId, matchId).flatMap {
      case Some(tournamentMatch) =>
        for {
          tournamentOpt <- tournamentService.getTournament(tournamentMatch.tournamentId)
          firstUserOpt <- userRepository.getById(tournamentMatch.firstUserId)
          secondUserOpt <- userRepository.getById(tournamentMatch.secondUserId)
        } yield Ok(views.html.fileUpload(
          user,
          tournamentId,
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
          tournamentId,
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

  private def processFilesAndAddToSession(user: User,
                                          tournamentId: Long,
                                          matchId: Long,
                                          uploadedFiles: Seq[MultipartFormData.FilePart[TemporaryFile]]
                                         )(implicit request: SecuredRequest[EnvType, MultipartFormData[TemporaryFile]]): Future[Result] = {

    val futureResults = uploadedFiles.map { file =>
      parseReplayFileService.validateAndProcessSingleFile(file)
    }.toList

    Future.sequence(futureResults).flatMap { fileResults =>
      val addFutures = fileResults.map { fileResult =>
        uploadSessionService.addFileToSession(user, matchId, fileResult)
      }

      Future.sequence(addFutures).flatMap { sessionResults =>
        uploadSessionService.getSession(user, matchId).flatMap {
          case Some(session) =>
            renderUploadFormWithMatchDetails(user, tournamentId, matchId, session)
          case None =>
            Future.successful(Ok(views.html.index(Some(user))))
        }
      }
    }
  }
}
