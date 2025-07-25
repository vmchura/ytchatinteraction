package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.Json
import play.api.libs.Files.TemporaryFile
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import services.{ParseReplayFileService, UploadSession, UploadSessionService, FileStorageService, StoredFileInfo}
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
                                      fileStorageService: FileStorageService,
                                      uploadedFileRepository: models.repository.UploadedFileRepository,
                                      tournamentService: services.TournamentService,
                                      userRepository: models.repository.UserRepository
                                    )(implicit ec: ExecutionContext) extends SilhouetteController(components) {

  private val logger = Logger(getClass)

  def uploadFormForMatch(tournamentId: Long, matchId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
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

  def uploadFile(tournamentId: Long, matchId: Long): Action[MultipartFormData[TemporaryFile]] = silhouette.SecuredAction.async(parse.multipartFormData) { implicit request =>
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
        
        // If storage successful, save record to database
        dbResult <- storageResult match {
          case Right(storedInfo) =>
            val uploadedFile = models.UploadedFile(
              userId = user.userId,
              tournamentId = tournamentId,
              matchId = matchId,
              sha256Hash = fileResult.sha256Hash.get,
              originalName = fileResult.fileName,
              relativeDirectoryPath = "uploads", // Based on configuration
              savedFileName = storedInfo.storedFileName,
              uploadedAt = storedInfo.storedAt
            )
            
            uploadedFileRepository.create(uploadedFile).map { createdFile =>
              logger.info(s"Saved file record to database: ID ${createdFile.id}, SHA256: ${createdFile.sha256Hash}")
              Right(storedInfo)
            }.recover { case ex =>
              logger.error(s"Failed to save file record to database for ${fileResult.fileName}: ${ex.getMessage}", ex)
              // File was stored but DB record failed - this is a partial success
              Right(storedInfo)
            }
          case Left(error) =>
            Future.successful(Left(error))
        }
      } yield dbResult
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

  private def processFilesAndAddToSession(user: User,
                                          tournamentId: Long,
                                          matchId: Long,
                                          uploadedFiles: Seq[MultipartFormData.FilePart[TemporaryFile]]
                                         )(implicit request: SecuredRequest[EnvType, MultipartFormData[TemporaryFile]]): Future[Result] = {

    // Get or create session first
    uploadSessionService.getOrCreateSession(user, matchId).flatMap { session =>
      
      val futureResults = uploadedFiles.map { file =>
        for {
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
          
          // Store the file if processing was successful
          storageResult <- storeProcessedFile(user, tournamentId, matchId, session.sessionId, fileResult, fileBytes)
          
          // Add to session
          sessionResult <- uploadSessionService.addFileToSession(user, matchId, fileResult)
          
        } yield (fileResult, storageResult, sessionResult)
      }.toList

      Future.sequence(futureResults).flatMap { results =>
        // Log storage results
        results.foreach { case (fileResult, storageResult, _) =>
          storageResult match {
            case Right(storedInfo) =>
              logger.info(s"Successfully stored file: ${storedInfo.originalFileName} at ${storedInfo.storedPath} (${storedInfo.size} bytes)")
            case Left(error) =>
              logger.error(s"Failed to store file ${fileResult.fileName}: $error")
          }
        }

        // Render the updated form
        uploadSessionService.getSession(user, matchId).flatMap {
          case Some(updatedSession) =>
            renderUploadFormWithMatchDetails(user, tournamentId, matchId, updatedSession)
          case None =>
            logger.warn(s"Session not found after processing files for user ${user.userId}, match $matchId")
            Future.successful(Ok(views.html.index(Some(user))))
        }
      }.recover { case ex: Exception =>
        logger.error(s"Error processing files for user ${user.userId}, match $matchId: ${ex.getMessage}", ex)
        Ok(views.html.index(Some(user)))
      }
    }
  }
}