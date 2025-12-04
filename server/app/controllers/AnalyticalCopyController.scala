package controllers

import evolutioncomplete.GameStateShared.{InvalidGame, PendingGame, ValidGame}
import evolutioncomplete.WinnerShared.Draw
import evolutioncomplete.{ParticipantShared, UploadStateShared}
import forms.Forms
import models.StarCraftModels.Terran
import models.repository.{AnalyticalFileRepository, UploadedFileRepository}
import services.UserSmurfService
import java.nio.file.Files
import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.{JsValue, Json, OWrites, Writes}
import play.api.libs.Files.TemporaryFile
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import services.*
import models.*
import play.silhouette.api.actions.SecuredRequest
import utils.auth.WithAdmin
import upickle.default.*
import java.nio.file.Path

import scala.util.Try
import java.time.LocalDateTime
import java.util.UUID
import models.StarCraftModels.ReplayParsed

@Singleton
class AnalyticalCopyController @Inject() (
    components: DefaultSilhouetteControllerComponents,
    parseReplayFileService: ParseReplayFileService,
    uploadSessionService: AnalyticalUploadSessionService,
    fileStorageService: FileStorageService,
    analyticalFileRepository: AnalyticalFileRepository,
    uploadedFileRepository: UploadedFileRepository,
    tournamentService: services.TournamentService,
    userRepository: models.repository.UserRepository,
    analyticalReplayService: AnalyticalReplayService,
    userSmurfService: UserSmurfService
)(implicit ec: ExecutionContext)
    extends SilhouetteController(components) {

  private val logger = Logger(getClass)

  def showUserOptions(userId: Long): Action[AnyContent] =
    silhouette.SecuredAction.async { implicit request =>
      for {
        groupedFiles <- analyticalFileRepository
          .findByUserId(request.identity.userId)
          .map { files =>
            files
              .groupBy(_.userRace)
              .map(u =>
                (u._1, u._2.groupBy(_.rivalRace).map(r => (r._1, r._2.length)))
              )
          }
        userSmurfs <- userSmurfService.getUserSmurfs(userId)
        matchSmurfs = userSmurfs.groupBy(_.matchId)
        matchFiles <- Future.traverse(matchSmurfs)(ms =>
          uploadedFileRepository
            .findByMatchId(ms._1)
            .map(files => (ms._1, files, ms._2))
        )
        matchSingleFile = matchFiles.flatMap {
          case (matchId, files, userSmurfs) =>
            files.map(f => (matchId, f, userSmurfs))
        }
        matchSingleFileParsed <- Future.traverse(matchSingleFile) {
          case (matchId, f, userSmurfs) =>
            parseReplayFileService
              .processSingleFile(
                Path.of(f.relativeDirectoryPath)
              )
              .map(_.map(parsed => (f, matchId, parsed, userSmurfs)))

        }
        validMatchSingleFile = matchSingleFileParsed.flatMap {
          case Some(
                (
                  singleFile,
                  matchId,
                  ReplayParsed(_, _, _, teams, _, _, _),
                  userSmurfs
                )
              ) =>

            val scPlayers = teams.flatMap(t => t.participants)
            if (scPlayers.length == 2) {
              val (user, rival) = scPlayers.partition(sc =>
                userSmurfs.exists(_.smurf.equals(sc.name))
              )
              Option.when(user.nonEmpty && rival.nonEmpty) {
                (singleFile, user.head, matchId, rival.head)

              }
            } else {
              None
            }
          case _ => None
        }

      } yield {
        Ok

      }

    }

  def updateState(): Action[MultipartFormData[TemporaryFile]] =
    silhouette.SecuredAction.async(parse.multipartFormData) {
      implicit request =>
        val session = request.body.files
          .find(_.key == "analyticalFile") match {
          case Some(part) =>
            for {
              processed <- parseReplayFileService
                .validateAndProcessSingleFile(part)
              newSession <- uploadSessionService.startSession(
                request.identity,
                processed
              )
            } yield {
              newSession
            }
          case None => Future.successful(None)
        }

        session.map {
          case None =>
            Redirect(
              routes.AnalyticalUploadController.uploadAnalyticalFile()
            )
          case Some(session) =>
            Ok(
              views.html.analyticalUploadSmurf(
                request.identity,
                session
              )
            )
        }
    }

  def finalizeSmurf(): Action[AnyContent] = silhouette.SecuredAction.async {
    implicit request =>
      Forms.analyticalFileDataForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Future.successful(
              Redirect(
                routes.AnalyticalUploadController.uploadAnalyticalFile()
              )
            )
          },
          analyticalFileData => {
            uploadSessionService.getSession(request.identity) match {
              case Some(session)
                  if session.sha256Hash.equals(analyticalFileData.fileHash) =>
                val analyticalFile = for {
                  userRace <- session
                    .userRaceGivenPlayerId(analyticalFileData.playerID)
                  rivalRace <- session
                    .rivalRaceGivenPlayerId(analyticalFileData.playerID)
                  frames <- session.frames
                } yield {
                  AnalyticalFile(
                    0,
                    request.identity.userId,
                    session.sha256Hash,
                    session.storageInfo.originalFileName,
                    session.storageInfo.storedPath,
                    session.storageInfo.storedFileName,
                    session.storageInfo.storedAt,
                    analyticalFileData.playerID,
                    userRace,
                    rivalRace,
                    frames,
                    None,
                    None
                  )
                }
                analyticalFile match {
                  case Some(af) =>
                    analyticalFileRepository.create(af).map { _ =>
                      Redirect(
                        routes.AnalyticalUploadController.uploadAnalyticalFile()
                      )
                    }
                  case None =>
                    Future.successful(
                      Redirect(
                        routes.AnalyticalUploadController.uploadAnalyticalFile()
                      )
                    )
                }

              case _ =>
                Future.successful(
                  Redirect(
                    routes.AnalyticalUploadController.uploadAnalyticalFile()
                  )
                )
            }
          }
        )
  }
}
