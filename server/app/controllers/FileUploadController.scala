package controllers

import evolutioncomplete.WinnerShared.Draw
import evolutioncomplete.{ParticipantShared, UploadStateShared}

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
  
  // Conversion methods
  def fromUploadSession(session: UploadSession): UploadSessionJson = {
    UploadSessionJson(
      sessionId = session.sessionId,
      matchId = session.matchId,
      totalFiles = session.totalFiles,
      successfulFiles = session.successfulFiles.map(fromFileProcessResult),
      failedFiles = session.failedFiles.map(fromFileProcessResult),
      hasFiles = session.hasFiles
    )
  }
  
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

  def uploadFormForMatch(tournamentId: Long, matchId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    val currentSession: UploadSession = uploadSessionService.getSession(request.identity, matchId) match {
      case Some(session) => session
      case None => uploadSessionService.startSession(request.identity, matchId)
    }

    for {
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

  def uploadFile(tournamentId: Long, matchId: Long): Action[MultipartFormData[TemporaryFile]] = silhouette.SecuredAction.async(parse.multipartFormData) { implicit request =>
    import FileUploadResponse._
    val currentSession = uploadSessionService.getOrCreateSession(request.identity, matchId)

    if (currentSession.isFinalized) {
      Future.successful(BadRequest(Json.toJson(FileUploadResponse(
        success = false,
        error = Some("Session is already finalized")
      ))))
    } else {
      val uploadedFiles = request.body.files.filter(_.key == "upload_file")
      if (uploadedFiles.isEmpty) {
        Future.successful(BadRequest(Json.toJson(FileUploadResponse(
          success = false,
          error = Some("No files provided")
        ))))
      } else {
        processFilesAndReturnJson(request.identity, tournamentId, matchId, uploadedFiles)(request)
      }
    }
  }

  def finalizeSession(tournamentId: Long, matchId: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()) { implicit request =>
    val user = request.identity
    uploadSessionService.finalizeSession(user, matchId) match {
      case _ =>
        Ok(views.html.index(Some(user)))
    }

  }

  def removeFile(tournamentId: Long, matchId: Long, sha256Hash: String): Action[AnyContent] = silhouette.SecuredAction { implicit request =>
    import FileUploadResponse._

    uploadSessionService.removeFileFromSession(request.identity, matchId, sha256Hash) match {
      case Some(updatedSession) =>
        Ok(Json.toJson(FileUploadResponse(
          success = true,
          session = Some(fromUploadSession(updatedSession))
        )))
      case None =>
        BadRequest(Json.toJson(FileUploadResponse(
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

  private def processFilesAndReturnJson(user: User,
                                        tournamentId: Long,
                                        matchId: Long,
                                        uploadedFiles: Seq[MultipartFormData.FilePart[TemporaryFile]]
                                       )(implicit request: SecuredRequest[EnvType, MultipartFormData[TemporaryFile]]): Future[Result] = {
    import FileUploadResponse._

    // Get or create session first
    val currentSession = uploadSessionService.getOrCreateSession(user, matchId)

    val futureResults = uploadedFiles.foldLeft(Future.successful(List.empty[FileProcessResult])) { case (previousFiles, file) =>
      for {
        filesProcessed <- previousFiles
        // Process the file
        fileResult <- parseReplayFileService.validateAndProcessSingleFile(file)

        // Read file bytes for storage
        fileBytes = try {
          java.nio.file.Files.readAllBytes(file.ref.path)
        } catch {
          case ex: Exception =>
            logger.error(s"Failed to read file ${file.filename}: ${ex.getMessage}", ex)
            Array.empty[Byte]
        }
        storageResult <- if (fileResult.success) storeProcessedFile(user, tournamentId, matchId, currentSession.sessionId, fileResult, fileBytes) else Future.successful(Left("Not saved, error parsing"))
        fileRulestWithStorage = storageResult match {
          case Left(_) => fileResult
          case Right(storeFileInfo) => fileResult.copy(storedFileInfo = Some(storeFileInfo))
        }
        sessionResult <- if (fileRulestWithStorage.success && fileRulestWithStorage.storedFileInfo.isDefined) uploadSessionService.addFileToSession(user, matchId, fileRulestWithStorage) else Future.successful(None)

      } yield filesProcessed :+ fileRulestWithStorage
    }

    for {
      _ <- futureResults

    } yield {
      val updatedSession = uploadSessionService.getSession(user, matchId)
      updatedSession match {
        case Some(session) =>
          Ok(Json.toJson(FileUploadResponse(
            success = true,
            session = Some(fromUploadSession(currentSession))
          )))
        case None =>
          logger.warn(s"Session not found after processing files for user ${user.userId}, match $matchId")
          InternalServerError(Json.toJson(FileUploadResponse(
            success = false,
            error = Some("Session not found after processing")
          )))
      }
    }
  }
  def mydata(): Action[AnyContent]=  silhouette.UserAwareAction.async { implicit request =>
    println(request.body)
    val responseValue = UploadStateShared(0,0,ParticipantShared(0,"ASD",Nil),ParticipantShared(0,"XYZ",Nil),Nil, Draw)
    Future.successful(Ok(write(responseValue)))
  }
}