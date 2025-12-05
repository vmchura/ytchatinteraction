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
import models.StarCraftModels.SCRace
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
                  ReplayParsed(_, _, _, teams, _, Some(frames), _),
                  userSmurfs
                )
              ) =>

            val scPlayers = teams.flatMap(t => t.participants)
            if (scPlayers.length == 2) {
              val (user, rival) = scPlayers.partition(sc =>
                userSmurfs.exists(_.smurf.equals(sc.name))
              )
              Option.when(user.nonEmpty && rival.nonEmpty) {
                (singleFile, user.head, matchId, rival.head, frames)

              }
            } else {
              None
            }
          case _ => None
        }

      } yield {
        val potentialAnalyticalFiles = validMatchSingleFile.map {
          case (singleFile, userPlayer, matchId, rivalPlayer, frames) =>
            PotentialAnalyticalFile(
              singleFile,
              userPlayer,
              matchId,
              rivalPlayer,
              frames
            )
        }
        Ok(
          views.html.analyticalCopy(
            request.identity,
            userId,
            groupedFiles,
            potentialAnalyticalFiles.toSeq
          )
        )

      }

    }

  def moveTournamentToAnalytical(
      uploadedFileId: Long,
      userUpdatedId: Long,
      userSlot: Int,
      userRace: SCRace,
      rivalRace: SCRace,
      frames: Int
  ): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    for {
      fileUploadedOption <- uploadedFileRepository.findById(uploadedFileId)
      fileUploaded <- fileUploadedOption.fold(
        Future.failed(new IllegalStateException("No file uploaded"))
      )(Future.successful)
      analyticalFile = AnalyticalFile(
        0,
        userUpdatedId,
        fileUploaded.sha256Hash,
        fileUploaded.originalName,
        fileUploaded.relativeDirectoryPath,
        fileUploaded.originalName,
        fileUploaded.uploadedAt,
        userSlot,
        userRace,
        rivalRace,
        frames,
        Some(request.identity.userId),
        None
      )
      moveAnalytica <- analyticalFileRepository.create(analyticalFile)
    } yield {
      Redirect(
        routes.AnalyticalCopyController.showUserOptions(userUpdatedId)
      )
    }

  }
}
