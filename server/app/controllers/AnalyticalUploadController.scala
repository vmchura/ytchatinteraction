package controllers

import evolutioncomplete.GameStateShared.{InvalidGame, PendingGame, ValidGame}
import evolutioncomplete.WinnerShared.Draw
import evolutioncomplete._
import forms.Forms
import models.StarCraftModels.Terran
import models.repository.AnalyticalFileRepository

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

import scala.util.Try
import java.time.LocalDateTime
import java.util.UUID

@Singleton
class AnalyticalUploadController @Inject() (
    components: DefaultSilhouetteControllerComponents,
    parseReplayFileService: ParseReplayFileService,
    uploadSessionService: AnalyticalUploadSessionService,
    fileStorageService: FileStorageService,
    analyticalFileRepository: AnalyticalFileRepository,
    tournamentService: services.TournamentService,
    userRepository: models.repository.UserRepository,
    analyticalReplayService: AnalyticalReplayService
)(implicit ec: ExecutionContext)
    extends SilhouetteController(components) {

  private val logger = Logger(getClass)

  def uploadAnalyticalFile(): Action[AnyContent] =
    silhouette.SecuredAction.async { implicit request =>
      analyticalFileRepository.findByUserId(request.identity.userId).map {
        files =>
          val groupedFiles = files
            .groupBy(_.userRace)
            .map(u =>
              (u._1, u._2.groupBy(_.rivalRace).map(r => (r._1, r._2.length)))
            )
          Ok(
            views.html.analyticalUpload(
              request.identity,
              groupedFiles
            )
          )
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
                MetaAnalyticalSession(request.identity.userId, processed)
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
            uploadSessionService.getSession(
              f"${request.identity.userId}"
            ) match {
              case Some(
                    session @ AnalyticalSession(
                      userId,
                      uploadState,
                      Some(storageInfo),
                      lastUpdated,
                      finalResult
                    )
                  ) if session.sha256Hash.equals(analyticalFileData.fileHash) =>
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
                    storageInfo.originalFileName,
                    storageInfo.storedPath,
                    storageInfo.storedFileName,
                    storageInfo.storedAt,
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
