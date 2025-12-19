package controllers

import evolutioncomplete.GameStateShared.{InvalidGame, PendingGame, ValidGame}
import evolutioncomplete.WinnerShared.Draw
import evolutioncomplete._
import java.nio.file.Files
import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.{JsValue, Json, OWrites, Writes}
import play.api.libs.Files.TemporaryFile
import play.api.Logger
import models._

import scala.concurrent.{ExecutionContext, Future}
import services._
import models.{TournamentMatch, User}
import play.silhouette.api.actions.SecuredRequest
import utils.auth.WithAdmin
import upickle.default.*
import scala.util.Try
import java.time.LocalDateTime
import java.util.UUID

@Singleton
class FileUploadController @Inject() (
    components: DefaultSilhouetteControllerComponents,
    parseReplayFileService: ParseReplayFileService,
    uploadSessionService: TournamentUploadSessionService,
    fileStorageService: FileStorageService,
    uploadedFileRepository: models.repository.UploadedFileRepository,
    tournamentService: services.TournamentService,
    userRepository: models.repository.UserRepository,
    userActivityService: UserActivityService
)(implicit ec: ExecutionContext)
    extends SilhouetteController(components) {

  private val logger = Logger(getClass)

  def removeFile(
      tournamentId: Long,
      matchId: Long,
      sessionUUID: UUID
  ): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    uploadSessionService
      .getOrCreateSession(
        MetaTournamentSession(request.identity.userId, matchId, tournamentId)
      )
      .map {
        case Some(session) =>
          val newState = uploadSessionService.persistState(
            uploadSessionService.removeFileFromSession(session, sessionUUID)
          )
          Ok(write(newState.uploadState))

        case None =>
          BadRequest(
            Json.toJson(
              Map(
                "error" -> "Session not available"
              )
            )
          )
      }
  }

  /** Store a processed file using FileStorageService and save record to
    * database
    */
  private def storeProcessedFile(
      session: TournamentSession,
      fileResult: services.FileProcessResult,
      fileBytes: Array[Byte]
  ): Either[String, StoredFileInfo] = {
    if (
      fileResult.success && fileBytes.nonEmpty && fileResult.sha256Hash.isDefined
    ) {
      for {
        // Store the file on disk
        storageResult <- fileStorageService.storeBasicFile(
          fileBytes = fileBytes,
          originalFileName = fileResult.fileName,
          contentType = fileResult.contentType,
          userId = session.userId,
          sessionUploadFile = session
        )
      } yield storageResult
    } else {
      Left(
        s"Cannot store failed file: ${fileResult.errorMessage.getOrElse("Unknown error")}"
      )
    }
  }
  def fetchState(
      challongeMatchID: Long,
      tournamentId: Long
  ): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    uploadSessionService
      .getOrCreateSession(
        MetaTournamentSession(
          request.identity.userId,
          challongeMatchID,
          tournamentId
        )
      )
      .map {
        case Some(session) => Ok(write(session.uploadState))
        case None          =>
          BadRequest(
            Json.toJson(
              Map(
                "error" -> "Session not available"
              )
            )
          )
      }
  }

  def updateState(): Action[MultipartFormData[TemporaryFile]] =
    silhouette.SecuredAction.async(parse.multipartFormData) {
      implicit request =>
        given User = request.identity
        val session = request.body.files
          .find(_.key == "state")
          .flatMap { part =>
            Try(
              read[TournamentUploadStateShared](
                new String(Files.readAllBytes(part.ref.path), "UTF-8")
              )
            ).toOption
          } match {
          case Some(value) =>

            userActivityService.trackUploadUser(value)
            uploadSessionService
              .getOrCreateSession(
                MetaTournamentSession(
                  request.identity.userId,
                  value.challongeMatchID,
                  value.tournamentID
                )
              )
              .map(_.map(_.withUploadStateShared(value)))
          case None => Future.successful(None)
        }
        session.flatMap {
          case None =>
            Future.successful(
              BadRequest(
                Json.toJson(
                  Map(
                    "error" -> "Session not available"
                  )
                )
              )
            )
          case Some(session) =>
            val replays = request.body.files.filter(_.key == "replays")
            val newSession = replays
              .foldLeft(Future.successful(session)) {
                case (currentSessionFut, newReplay) =>
                  for {
                    currentSession <- currentSessionFut
                    fileProcessResult <- parseReplayFileService
                      .validateAndProcessSingleFile(newReplay)
                    newSession <- uploadSessionService.addFileToSession(
                      currentSession,
                      fileProcessResult
                    )
                  } yield {
                    newSession
                  }
              }
              .map { session =>
                uploadSessionService.persistState(session)
              }

            newSession.map(sessionUpdated => {
              userActivityService.trackResponseServer(
                sessionUpdated.uploadState
              )
              Ok(write[TournamentUploadStateShared](sessionUpdated.uploadState))
            })

        }
    }

  def uploadFormForMatch(
      tournamentId: Long,
      challengeMatchId: Long
  ): Action[AnyContent] = silhouette.SecuredAction { implicit request =>
    Ok(
      views.html.fileUpload(
        request.identity,
        tournamentId,
        challengeMatchId
      )
    )

  }
}
