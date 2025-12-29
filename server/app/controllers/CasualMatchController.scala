package controllers

import evolutioncomplete.*
import GameStateShared.*
import play.api.mvc.*

import javax.inject.*
import services._
import models.*

import java.util.UUID
import java.nio.file.Files
import play.api.mvc.*
import play.api.Logger
import upickle.default.*
import play.api.libs.json.*
import play.api.libs.Files.TemporaryFile

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import models.repository._
import forms.Forms
import models.MatchStatus.*
import utils.auth.WithAdmin

@Singleton
class CasualMatchController @Inject() (
    components: DefaultSilhouetteControllerComponents,
    uploadSessionService: CasualMatchUploadSessionService,
    fileStorageService: FileStorageService,
    parseReplayFileService: ParseReplayFileService,
    userSmurfService: UserSmurfService,
    uploadedFileRepository: CasualMatchFileRepository,
    casualMatchRepository: CasualMatchRepository,
    analyticalReplayService: AnalyticalReplayService,
    userAliasRepository: UserAliasRepository,
    analyticalResultRepository: AnalyticalResultRepository,
    userActivityService: UserActivityService
)(implicit ec: ExecutionContext)
    extends SilhouetteController(components) {
  private val logger = Logger(getClass)

  def removeFile(
      casualMatchId: Long,
      sessionUUID: UUID
  ): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    given User = request.identity
    given SessionBrowser = SessionBrowser(request.session.get("sid").getOrElse("unknown"))
    uploadSessionService
      .getOrCreateSession(
        MetaCasualMatchSession(request.identity.userId, casualMatchId)
      )
      .map {
        case Some(session) =>
          val newState = uploadSessionService.persistState(
            uploadSessionService.removeFileFromSession(session, sessionUUID)
          )
          userActivityService.trackResponseServer(
            newState.uploadState
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
    given User = request.identity
    given SessionBrowser = SessionBrowser(request.session.get("sid").getOrElse("unknown"))
    uploadSessionService
      .getOrCreateSession(
        MetaCasualMatchSession(
          request.identity.userId,
          casualMatchId
        )
      )
      .map {
        case Some(session) =>

          userActivityService.trackResponseServer(
            session.uploadState
          )
          Ok(write(session.uploadState))
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

  def updateState(): Action[MultipartFormData[TemporaryFile]] =
    silhouette.SecuredAction.async(parse.multipartFormData) {
      implicit request =>
        given User = request.identity
        given SessionBrowser = SessionBrowser(request.session.get("sid").getOrElse("unknown"))
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
            userActivityService.trackUploadUser(value)
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

            newSession.map(sessionUpdated => {
              userActivityService.trackResponseServer(
                sessionUpdated.uploadState
              )
              Ok(write[CasualMatchStateShared](sessionUpdated.uploadState))
            })

        }
    }

  def uploadFormForMatch(
      casualMatchID: Long
  ): Action[AnyContent] = silhouette.SecuredAction { implicit request =>
    Ok(
      views.html.fileUploadCasualMatch(
        request.identity,
        casualMatchID
      )
    )
  }

  def viewFindUser(): Action[AnyContent] = silhouette.SecuredAction.async {
    implicit request =>
      userAliasRepository.list().map { allUserAlias =>
        val mapUserAlias = allUserAlias
          .groupBy(_.userId)
          .map {
            case (userId, list) => {
              val sortedList = list.toList.sortBy(_.assignedAt).reverse
              if (sortedList.tail.isEmpty)
                (sortedList.head, Nil)
              else {
                (sortedList.head, sortedList.tail.dropRight(1))
              }
            }
          }
          .toList

        Ok(
          views.html.viewNewCasualMatch(
            request.identity,
            mapUserAlias.filter(_._1.userId != request.identity.userId)
          )
        )
      }
  }

  def createCasualMatch(rivalID: Long): Action[AnyContent] =
    silhouette.SecuredAction.async { implicit request =>
      given User = request.identity
      given SessionBrowser = SessionBrowser(request.session.get("sid").getOrElse("unknown"))
      for {
        casualMatch <- casualMatchRepository.create(
          CasualMatch(
            0,
            request.identity.userId,
            rivalID,
            None,
            java.time.Instant.now(),
            Pending
          )
        )
        sessionCreated <- uploadSessionService.startSession(
          MetaCasualMatchSession(rivalID, casualMatch.id)
        )
      } yield {
        sessionCreated match {
          case Some(session) =>
            userActivityService.trackResponseServer(
              session.uploadState
            )
            Redirect(
              routes.CasualMatchController.uploadFormForMatch(casualMatch.id)
            )
          case _ =>
            Redirect(routes.CasualMatchController.viewFindUser())
              .flashing("error" -> "No se pudo crear el VS casual")
        }

      }
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
            case vg @ ValidGame(smurfs, _, _, _, _, _) =>

              val firstPlayerSlot = vg.slotBySmurf(smurfsFirstParticipant)
              val secondPlayerSlot = vg.slotBySmurf(smurfsSecondParticipant)

              (firstPlayerSlot, secondPlayerSlot) match {
                case (Some(s1), Some(s2)) => s1.id != s2.id
                case _                    => false
              }
            case _ => false
          }
          .collect { case v: ValidGame =>
            v
          }
          .flatMap {
            case vg @ ValidGame(
                  smurfs,
                  mapName,
                  playedAt,
                  hash,
                  sessionID,
                  frames
                ) =>
              val firstPlayerSlot = vg.slotBySmurf(smurfsFirstParticipant)
              val secondPlayerSlot = vg.slotBySmurf(smurfsSecondParticipant)

              session.hash2StoreInformation
                .get(hash)
                .map((hash, _, firstPlayerSlot, secondPlayerSlot))
                .map {
                  case (hash, storedInfo, Some(userSlot), Some(rivalSlot)) =>
                    val uploadedFile = models.CasualMatchFile(
                      casualMatchId = session.casualMatchId,
                      sha256Hash = hash,
                      originalName = storedInfo.originalFileName,
                      relativeDirectoryPath = storedInfo.storedPath,
                      savedFileName = storedInfo.storedFileName,
                      uploadedAt = storedInfo.storedAt,
                      slotPlayerId = userSlot.id,
                      rivalSlotPlayerId = rivalSlot.id,
                      userRace = userSlot.race,
                      rivalRace = rivalSlot.race,
                      gameFrames = frames
                    )

                    uploadedFileRepository.create(uploadedFile).map {
                      createdFile =>
                        logger.info(
                          s"Saved file record to database: ID ${createdFile.id}, SHA256: ${createdFile.sha256Hash}"
                        )
                        1
                    }

                  case _ => Future.successful(0)

                }
          }
      )
      .map(_.sum)
  }

  def closeMatch(
      casualMatchId: Long
  ): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    given User = request.identity
    given SessionBrowser = SessionBrowser(request.session.get("sid").getOrElse("unknown"))
    Forms.closeMatchForm
      .bindFromRequest()
      .fold(
        formWithErrors => {
          Future.successful(Redirect(routes.UserEventsController.userEvents()))
        },
        winnerData => {
          userActivityService.trackFormSubmit("casual_close", winnerData)
          for {
            casualMatchOption <- casualMatchRepository
              .findById(casualMatchId)
            casualMatch <- casualMatchOption match {
              case Some(
                    cm @ CasualMatch(
                      _,
                      _,
                      _,
                      _,
                      _,
                      Pending | InProgress
                    )
                  ) =>
                Future.successful(cm)
              case Some(_) =>
                Future
                  .failed(new IllegalStateException("Match already resolved"))
              case _ =>
                Future.failed(new IllegalStateException("Match not found"))
            }
            currentSessionOption = uploadSessionService.getSession(
              f"${request.identity.userId}_${casualMatchId}"
            )
            currentSession <- currentSessionOption match {
              case Some(session) => Future.successful(session)
              case _             =>
                Future.failed(new IllegalStateException("No session found"))
            }
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
            _ <- casualMatchRepository
              .setWinner(casualMatch.withWinner(winnerData.winner))
            resultAnalytical <- analyticalReplayService
              .analyticalProcessCasualMatch(casualMatchId)

          } yield {
            Redirect(routes.CasualMatchController.viewResults(casualMatchId))
              .flashing("success" -> s"Resultado actualizado")
          }
        }
      )
  }

  def reRunAnalyticalProcess(casualMatchID: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async{ implicit request =>
    analyticalReplayService.analyticalProcessCasualMatch(casualMatchID).map{ _ =>
      Redirect(routes.CasualMatchController.viewResults(casualMatchID)).flashing("success" -> "Resultado actualizado")
    }
  }

  def viewResults(casualMatchID: Long): Action[AnyContent] =
    silhouette.SecuredAction.async { implicit request =>
      for {
        analyticalResults <- analyticalResultRepository.findByCasualMatchId(
          casualMatchID
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
