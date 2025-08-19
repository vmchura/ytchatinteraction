package controllers

import evolutioncomplete.GameStateShared.{InvalidGame, PendingGame, ValidGame}
import evolutioncomplete.WinnerShared.Draw
import evolutioncomplete.{ParticipantShared, UploadStateShared}
import java.nio.file.Files
import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.{JsValue, Json, OWrites, Writes}
import play.api.libs.Files.TemporaryFile
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import services.{FileProcessResult, FileStorageService, ParseReplayFileService, StoredFileInfo, UploadSession, UploadSessionService}
import models.{TournamentMatch, User}
import play.silhouette.api.actions.SecuredRequest
import utils.auth.WithAdmin
import upickle.default.*
import scala.util.Try
import java.time.LocalDateTime
import java.util.UUID

case class FileUploadState(
                            message: String,
                            uploadType: String
                          )

object FileUploadState {
  implicit val fileUploadStateWrites: play.api.libs.json.OWrites[FileUploadState] = Json.writes[FileUploadState]
}

// JSON response models for AJAX file upload
case class FileUploadResponse(
                               success: Boolean,
                               session: Option[UploadSessionJson] = None,
                               error: Option[String] = None
                             )

case class UploadSessionJson(
                              sessionId: String,
                              matchId: Long,
                              totalFiles: Int,
                              successfulFiles: List[FileResultJson],
                              failedFiles: List[FileResultJson],
                              hasFiles: Boolean
                            )

case class FileResultJson(
                           fileName: String,
                           success: Boolean,
                           sha256Hash: Option[String],
                           errorMessage: Option[String],
                           gameInfo: Option[GameInfoJson]
                         )

case class GameInfoJson(
                         mapName: Option[String],
                         player1: Option[PlayerJson],
                         player2: Option[PlayerJson],
                         startTime: Option[String]
                       )

case class PlayerJson(
                       name: String,
                       race: String
                     )

object FileUploadResponse {

  import models.StarCraftModels._

  implicit val playerJsonWrites: OWrites[PlayerJson] = Json.writes[PlayerJson]
  implicit val gameInfoJsonWrites: OWrites[GameInfoJson] = Json.writes[GameInfoJson]
  implicit val fileResultJsonWrites: OWrites[FileResultJson] = Json.writes[FileResultJson]
  implicit val uploadSessionJsonWrites: OWrites[UploadSessionJson] = Json.writes[UploadSessionJson]
  implicit val fileUploadResponseWrites: OWrites[FileUploadResponse] = Json.writes[FileUploadResponse]


  def fromFileProcessResult(file: FileProcessResult): FileResultJson = {
    FileResultJson(
      fileName = file.fileName,
      success = file.success,
      sha256Hash = file.sha256Hash,
      errorMessage = file.errorMessage,
      gameInfo = file.gameInfo.collect {
        case replayParsed: ReplayParsed => fromReplayParsed(replayParsed)
      }
    )
  }

  def fromReplayParsed(replay: ReplayParsed): GameInfoJson = {
    val player1 = for {
      team <- replay.teams.headOption
      participant <- team.participants.headOption
    } yield PlayerJson(
      name = participant.name,
      race = raceToString(participant.race)
    )

    val player2 = for {
      team <- replay.teams.lift(1)
      participant <- team.participants.headOption
    } yield PlayerJson(
      name = participant.name,
      race = raceToString(participant.race)
    )

    GameInfoJson(
      mapName = replay.mapName,
      player1 = player1,
      player2 = player2,
      startTime = replay.startTime
    )
  }

  private def raceToString(race: SCRace): String = race match {
    case Zerg => "Z"
    case Terran => "T"
    case Protoss => "P"
  }
}

@Singleton
class FileUploadController @Inject()(
                                      components: DefaultSilhouetteControllerComponents,
                                      parseReplayFileService: ParseReplayFileService,
                                      uploadSessionService: UploadSessionService,
                                      fileStorageService: FileStorageService,
                                      uploadedFileRepository: models.repository.UploadedFileRepository,
                                      tournamentService: services.TournamentService,
                                      userRepository: models.repository.UserRepository
                                    )(implicit ec: ExecutionContext) extends SilhouetteController(components) {

  private val logger = Logger(getClass)


  def removeFile(tournamentId: Long, matchId: Long, sha256Hash: UUID): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    import FileUploadResponse._
    uploadSessionService.getOrCreateSession(request.identity, matchId, tournamentId).map {
      case Some(session) =>
        val newState = uploadSessionService.persistState(uploadSessionService.removeFileFromSession(session, sha256Hash))
        Ok(write(newState.uploadState))

      case None => BadRequest(Json.toJson(FileUploadResponse(
        success = false,
        error = Some("File not found or session not available")
      )))
    }
  }

  /**
   * Store a processed file using FileStorageService and save record to database
   */
  private def storeProcessedFile(
                                  user: User,
                                  tournamentId: Long,
                                  matchId: Long,
                                  sessionId: String,
                                  fileResult: services.FileProcessResult,
                                  fileBytes: Array[Byte]
                                ): Future[Either[String, StoredFileInfo]] = {
    if (fileResult.success && fileBytes.nonEmpty && fileResult.sha256Hash.isDefined) {
      for {
        // Store the file on disk
        storageResult <- fileStorageService.storeFile(
          fileBytes = fileBytes,
          originalFileName = fileResult.fileName,
          contentType = fileResult.contentType,
          userId = user.userId,
          matchId = matchId,
          sessionId = sessionId
        )
      } yield storageResult
    } else {
      Future.successful(Left(s"Cannot store failed file: ${fileResult.errorMessage.getOrElse("Unknown error")}"))
    }
  }
  def fetchState(challongeMatchID: Long, tournamentId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    uploadSessionService.getOrCreateSession(request.identity, challongeMatchID, tournamentId).map {
      case Some(session) => Ok(write(session.uploadState))
      case None => BadRequest(Json.toJson(FileUploadResponse(
        success = false,
        error = Some("Session not available")
      )))
    }
  }

  def updateState(): Action[MultipartFormData[TemporaryFile]] = silhouette.SecuredAction.async(parse.multipartFormData) { implicit request =>

    val session = request.body.files
      .find(_.key == "state")
      .flatMap { part =>
        Try(read[UploadStateShared](new String(Files.readAllBytes(part.ref.path), "UTF-8"))).toOption
      } match {
      case Some(value) =>
        uploadSessionService.getOrCreateSession(request.identity, value.challongeMatchID, value.tournamentID).map(_.map(_.withUploadStateShared(value)))
      case None => Future.successful(None)
    }
    session.flatMap{
      case None => Future.successful(BadRequest(Json.toJson(FileUploadResponse(
        success = false,
        error = Some("Session not available")
      ))))
      case Some(session) =>
        val replays = request.body.files.filter(_.key == "replays")
        val newSession = replays.foldLeft(Future.successful(session)){ case (currentSessionFut, newReplay) =>
          for{
            currentSession <- currentSessionFut
            fileProcessResult <- parseReplayFileService.validateAndProcessSingleFile(newReplay)
            newSession <- uploadSessionService.addFileToSession(currentSession, fileProcessResult)
          }yield{
            newSession
          }
        }.map{ session =>
          uploadSessionService.persistState(session)
        }
        
        newSession.map(sessionUpdated => Ok(write[UploadStateShared](sessionUpdated.uploadState)))

    }
  }

  def uploadFormForMatch(tournamentId: Long, challengeMatchId: Long): Action[AnyContent] = silhouette.SecuredAction { implicit request =>

    Ok(views.html.fileUpload(
      request.identity,
      tournamentId,
      challengeMatchId
    ))


  }
}