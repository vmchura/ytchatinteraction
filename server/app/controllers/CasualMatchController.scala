package controllers

import evolutioncomplete._
import GameStateShared._
import play.api.mvc._
import javax.inject._
import services.CasualMatchUploadSessionService
import models._
import java.util.UUID
import java.nio.file.Files
import play.api.mvc.*
import play.api.Logger
import upickle.default._
import play.api.libs.json._
import play.api.libs.Files.TemporaryFile
import scala.concurrent.{ExecutionContext, Future}
import services.FileStorageService
import scala.util.Try
import services.ParseReplayFileService
import services.UserSmurfService

@Singleton
class CasualMatchController @Inject() (
    components: DefaultSilhouetteControllerComponents,
    uploadSessionService: CasualMatchUploadSessionService,
    fileStorageService: FileStorageService,
    parseReplayFileService: ParseReplayFileService,
    userSmurfService: UserSmurfService
)(implicit ec: ExecutionContext)
    extends SilhouetteController(components) {
  private val logger = Logger(getClass)

  def removeFile(
      casualMatchId: Long,
      sessionUUID: UUID
  ): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    uploadSessionService
      .getOrCreateSession(
        MetaCasualMatchSession(request.identity.userId, casualMatchId)
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

  private def storeProcessedFile(
      session: CasualMatchSession,
      fileResult: services.FileProcessResult,
      fileBytes: Array[Byte]
  ): Either[String, CasualMatchFileInfo] = {
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
      casualMatchId: Long
  ): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    uploadSessionService
      .getOrCreateSession(
        MetaCasualMatchSession(
          request.identity.userId,
          casualMatchId
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
        val session = request.body.files
          .find(_.key == "state")
          .flatMap { part =>
            Try(
              read[CasualMatchStateShared](
                new String(Files.readAllBytes(part.ref.path), "UTF-8")
              )
            ).toOption
          } match {
          case Some(value) =>
            uploadSessionService
              .getOrCreateSession(
                MetaCasualMatchSession(
                  request.identity.userId,
                  value.casualMatchId
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

            newSession.map(sessionUpdated =>
              Ok(write[CasualMatchStateShared](sessionUpdated.uploadState))
            )

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

  private def recordMatchSmurfs(
      casualMatch: CasualMatch,
      firstParticipantSmurfs: Set[String],
      secondParticipantSmurfs: Set[String]
  ): Future[Seq[CasualUserSmurf]] = {
    userSmurfService.recordCasualMatchSmurfs(
      casualMatch.id,
      ParticipantShared(
        casualMatch.userId,
        "",
        firstParticipantSmurfs
      ),
      ParticipantShared(
        casualMatch.rivalUserId,
        "",
        secondParticipantSmurfs
      )
    )
  }

  private def persistMetaDataSessionFiles(
      session: CasualMatchSession,
      smurfsFirstParticipant: Set[String],
      smurfsSecondParticipant: Set[String]
  ): Future[Int] = {
    Future
      .sequence(
        session.uploadState.games
          .filter {
            case vg @ ValidGame(smurfs, _, _, _, _) =>

              val firstPlayerSlot = vg.slotBySmurf(smurfsFirstParticipant) 
              val secondPlayerSlot = vg.slotBySmurf(smurfsSecondParticipant)

              (firstPlayerSlot, secondPlayerSlot) match {
                case (Some(s1), Some(s2)) => s1 != s2
                case _                    => false
              }
            case _ => false
          }
          .collect { case v: ValidGame =>
            v
          }
          .flatMap {
            case vg @ ValidGame(smurfs, mapName, playedAt, hash, sessionID) =>
              val firstPlayerSlot = vg.slotBySmurf(smurfsFirstParticipant) 
              val secondPlayerSlot = vg.slotBySmurf(smurfsSecondParticipant)

              session.hash2StoreInformation.get(hash).map((hash, _, firstPlayerSlot, secondPlayerSlot)).map {
                case (hash, storedInfo, Some(userSlot), Some(rivalSlot)) =>
                  val uploadedFile = models.CasualMatchFile(
                    casualMatchId = session.casualMatchId,
                    sha256Hash = hash,
                    originalName = storedInfo.originalFileName,
                    relativeDirectoryPath = storedInfo.storedPath,
                    savedFileName = storedInfo.storedFileName,
                    uploadedAt = storedInfo.storedAt,
                    slotPlayerId = userSlot
                  )

                  uploadedFileRepository.create(uploadedFile).map {
                    createdFile =>
                      logger.info(
                        s"Saved file record to database: ID ${createdFile.id}, SHA256: ${createdFile.sha256Hash}"
                      )
                      1
                  }

              }
          }
      )
      .map(_.sum)
  }

  def closeMatch(
      challongeMatchID: Long,
      tournamentId: Long
  ): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    Forms.closeMatchForm
      .bindFromRequest()
      .fold(
        formWithErrors => {
          Future.successful(Redirect(routes.UserEventsController.userEvents()))
        },
        winnerData => {
          for {
            tournamentMatchOption <- tournamentService
              .getMatch(tournamentId, challongeMatchID)
            casualMatch <- tournamentMatchOption match {
              case Some(
                    tm @ TournamentMatch(
                      _,
                      _,
                      _,
                      _,
                      _,
                      Pending | InProgress,
                      _,
                      _
                    )
                  ) =>
                Future.successful(tm)
              case Some(_) =>
                Future
                  .failed(new IllegalStateException("Match already resolved"))
              case _ =>
                Future.failed(new IllegalStateException("Match not found"))
            }
            currentSessionOption = uploadSessionService.getSession(
              f"${request.identity.userId}_${challongeMatchID}_${tournamentId}"
            )
            currentSession <- currentSessionOption match {
              case Some(session) => Future.successful(session)
              case _             =>
                Future.failed(new IllegalStateException("No session found"))
            }
            result <- tournamentService
              .submitMatchResult(casualMatch, winnerData.winner)
            firstParticipantSmurfs = winnerData.smurfsFirstParticipant.toSet
            secondParticipantSmurfs = winnerData.smurfsSecondParticipant.toSet
            _ <- recordMatchSmurfs(
              casualMatch,
              firstParticipantSmurfs = firstParticipantSmurfs,
              secondParticipantSmurfs = secondParticipantSmurfs
            )
            _ <- persistMetaDataSessionFiles(
              currentSession,
              firstParticipantSmurfs,
              secondParticipantSmurfs
            )
            _ = uploadSessionService.finalizeSession(currentSession)
          } yield {
            analyticalReplayService
              .analyticalProcessMatch(tournamentId, challongeMatchID)
            Redirect(routes.UserEventsController.userEvents())
              .flashing("success" -> s"Resultado actualizado")
          }
        }
      )
  }

  def viewResults(challongeMatchID: Long): Action[AnyContent] =
    silhouette.SecuredAction.async { implicit request =>
      for {
        analyticalResults <- analyticalResultRepository.findByMatchId(
          challongeMatchID
        )
        distinctUsers = analyticalResults.map(_.userId).distinct
        userAlias <- Future.sequence(
          distinctUsers.map(userID =>
            userAliasRepository
              .getCurrentAlias(userID)
              .map(r => r.map(v => userID -> v))
          )
        )
        validUserAlias = userAlias.flatten.toMap
      } yield {
        Ok(
          views.html.singleMatchResult(
            request.identity,
            analyticalResults
              .flatMap(ar =>
                validUserAlias
                  .get(ar.userId)
                  .map(alias =>
                    AnalyticalResultView(
                      alias,
                      ar.userRace,
                      ar.rivalRace,
                      ar.originalFileName,
                      ar.analysisStartedAt,
                      ar.analysisFinishedAt,
                      ar.algorithmVersion,
                      ar.result
                    )
                  )
              )
              .toList
              .sortBy(_.originalFileName)
          )
        )
      }
    }

}
