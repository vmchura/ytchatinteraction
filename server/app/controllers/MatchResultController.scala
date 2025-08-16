package controllers

import models.repository.UploadedFileRepository
import models.{TournamentMatch, UserSmurf}

import javax.inject.*
import play.api.mvc.*
import play.api.data.*
import play.api.data.Forms.*
import services.{FileProcessResult, UploadSession}

import scala.concurrent.{ExecutionContext, Future}
import utils.auth.WithAdmin

case class MatchResultForm(
                            winnerId: Option[Long],
                            resultType: String,
                            inGameSmurfFirst: Option[String],
                            inGameSmurfSecond: Option[String]
                          )

@Singleton
class MatchResultController @Inject()(components: DefaultSilhouetteControllerComponents,
                                      tournamentService: services.TournamentService,
                                      userSmurfService: services.UserSmurfService,
                                      uploadSessionService: services.UploadSessionService,
                                      uploadedFileRepository: UploadedFileRepository
                                     )(implicit ec: ExecutionContext) extends SilhouetteController(components) {

  private val matchResultForm = Form(
    mapping(
      "winnerId" -> optional(longNumber),
      "resultType" -> nonEmptyText,
      "in_game_smurf_first" -> optional(nonEmptyText),
      "in_game_smurf_second" -> optional(nonEmptyText)
    )(MatchResultForm.apply)(r => Some((r.winnerId, r.resultType, r.inGameSmurfFirst, r.inGameSmurfSecond)))
  )
  private def recordMatchSmurfs(matchId: Long, tournamentId: Long, tournamentMatch: TournamentMatch, firstSmurf: String, secondSmurf: String): Future[Seq[UserSmurf]] = {
    userSmurfService.recordMatchSmurfs(
      matchId,
      tournamentId,
      tournamentMatch.firstUserId,
      firstSmurf,
      tournamentMatch.secondUserId,
      secondSmurf
    )
  }
  private def persistMetaDataSessionFiles(session: UploadSession, tournamentId: Long, files: List[FileProcessResult]): Future[List[Int]] = {
    Future.sequence(files.map(fileResult => {
      fileResult.storedFileInfo match {
        case Some(storedInfo) =>
          val uploadedFile = models.UploadedFile(
            userId = session.userId,
            tournamentId = tournamentId,
            matchId = session.matchId,
            sha256Hash = fileResult.sha256Hash.get,
            originalName = fileResult.fileName,
            relativeDirectoryPath = "uploads", // Based on configuration
            savedFileName = storedInfo.storedFileName,
            uploadedAt = storedInfo.storedAt
          )

          uploadedFileRepository.create(uploadedFile).map { createdFile =>
            logger.info(s"Saved file record to database: ID ${createdFile.id}, SHA256: ${createdFile.sha256Hash}")
            1
          }.recover { case ex =>
            logger.error(s"Failed to save file record to database for ${fileResult.fileName}: ${ex.getMessage}", ex)
            0
          }
        case None => Future.successful(0)
      }

    }))
  }
  private def persistMetaDataSessionFiles(tournamentId: Long, matchId: Long): Future[Int] = {
    val sessions = uploadSessionService.getSessionsForMatch(matchId)
    for{
      persistence <- Future.sequence(sessions.map(session => persistMetaDataSessionFiles(session, tournamentId, session.uploadedFiles.filter(_.storedFileInfo.isDefined))))
    }yield{
      persistence.flatten.sum
    }
  }
  def submitResult(tournamentId: Long, matchId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    matchResultForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(
          Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
            .flashing("error" -> "Invalid form data")
        )
      }, {
        case MatchResultForm(Some(winnerId), "with_winner", Some(firstSmurf), Some(secondSmurf)) =>
          // First get the match information to identify the users
          tournamentService.getMatch(tournamentId, matchId).flatMap {
            case Some(tournamentMatch) =>
              for {
                _ <- recordMatchSmurfs(matchId, tournamentId, tournamentMatch, firstSmurf, secondSmurf)
                result <- tournamentService.submitMatchResult(tournamentId, matchId, Some(winnerId), "with_winner")
                _ <- persistMetaDataSessionFiles(tournamentId, matchId)
              } yield result match {
                case Right(_) =>
                  Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                    .flashing("success" -> "Match result and smurfs submitted successfully")
                case Left(error) =>
                  Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                    .flashing("error" -> s"Failed to submit result: $error")
              }
            case None =>
              Future.successful(
                Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                  .flashing("error" -> "Match not found")
              )
          }

        case MatchResultForm(None, "tie", Some(firstSmurf), Some(secondSmurf)) =>
          // Handle tie case with smurfs
          tournamentService.getMatch(tournamentId, matchId).flatMap {
            case Some(tournamentMatch) =>
              for {
                _ <- recordMatchSmurfs(matchId, tournamentId, tournamentMatch, firstSmurf, secondSmurf)
                result <- tournamentService.submitMatchResult(tournamentId, matchId, None, "tie")
                _ <- persistMetaDataSessionFiles(tournamentId, matchId)
              } yield result match {
                case Right(_) =>
                  Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                    .flashing("success" -> "Match result submitted as tie with smurfs recorded")
                case Left(error) =>
                  Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                    .flashing("error" -> s"Failed to submit result: $error")
              }
            case None =>
              Future.successful(
                Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                  .flashing("error" -> "Match not found")
              )
          }

        case MatchResultForm(None, "cancelled", _, _) =>
          tournamentService.submitMatchResult(tournamentId, matchId, None, "cancelled").map {
            case Right(_) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("success" -> "Match cancelled successfully")
            case Left(error) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("error" -> s"Failed to cancel match: $error")
          }

        case _ =>
          Future.successful(
          Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
            .flashing("error" -> "Invalid form data - both smurfs are required for winner and tie results")
        )
      }
    )
  }

}
