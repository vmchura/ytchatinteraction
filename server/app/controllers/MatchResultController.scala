package controllers

import models.repository.UploadedFileRepository
import models.{MatchStatus, TournamentMatch, UserSmurf}

import javax.inject.*
import play.api.mvc.*
import play.api.data.*
import play.api.data.Forms.*
import services.{FileProcessResult, UploadSession}
import evolutioncomplete.GameStateShared.ValidGame
import evolutioncomplete.ParticipantShared
import evolutioncomplete.WinnerShared.*
import forms.Forms
import models.MatchStatus.{InProgress, Pending}

import scala.concurrent.{ExecutionContext, Future}
import utils.auth.WithAdmin


@Singleton
class MatchResultController @Inject()(components: DefaultSilhouetteControllerComponents,
                                      tournamentService: services.TournamentService,
                                      userSmurfService: services.UserSmurfService,
                                      uploadSessionService: services.UploadSessionService,
                                      uploadedFileRepository: UploadedFileRepository
                                     )(implicit ec: ExecutionContext) extends SilhouetteController(components) {

  private def recordMatchSmurfs(tournamentMatch: TournamentMatch,
                                firstParticipantSmurfs: Set[String],
                                secondParticipantSmurfs: Set[String]): Future[Seq[UserSmurf]] = {
    userSmurfService.recordMatchSmurfs(
      tournamentMatch.matchId,
      tournamentMatch.tournamentId,
      ParticipantShared(tournamentMatch.firstUserId, "", firstParticipantSmurfs),
      ParticipantShared(tournamentMatch.secondUserId, "", secondParticipantSmurfs)
    )
  }

  private def persistMetaDataSessionFiles(session: UploadSession): Future[Int] = {
    Future.sequence(session.uploadState.games.filter {
      case ValidGame(_, _, _, _, _) => true
      case _ => false
    }.flatMap {
      case ValidGame(_, _, _, hash, _) => Some(hash)
      case _ => None
    }.flatMap(hash => session.hash2StoreInformation.get(hash).map((hash, _)).map { case (hash, storedInfo) =>
      val uploadedFile = models.UploadedFile(
        userId = session.userId,
        tournamentId = session.uploadState.tournamentID,
        matchId = session.challongeMatchID,
        sha256Hash = hash,
        originalName = storedInfo.originalFileName,
        relativeDirectoryPath = storedInfo.storedPath,
        savedFileName = storedInfo.storedFileName,
        uploadedAt = storedInfo.storedAt
      )

      uploadedFileRepository.create(uploadedFile).map { createdFile =>
        logger.info(s"Saved file record to database: ID ${createdFile.id}, SHA256: ${createdFile.sha256Hash}")
        1
      }

    })).map(_.sum)
  }

  def closeMatch(challongeMatchID: Long, tournamentId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    Forms.closeMatchForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.index(Some(request.identity))))
      },
      winnerData => {
        for{
          tournamentMatchOption <- tournamentService.getMatch(tournamentId, challongeMatchID)
          tournamentMatch <- tournamentMatchOption match {
            case Some(tm @ TournamentMatch(_, _, _, _, _, _, Pending | InProgress)) => Future.successful(tm)
            case Some(_) => Future.failed(new IllegalStateException("Match already resolved"))
            case _ => Future.failed(new IllegalStateException("Match not found"))
          }
          currentSessionOption = uploadSessionService.getSession(request.identity, challongeMatchID, tournamentId)
          currentSession <- currentSessionOption match {
            case Some(session) => Future.successful(session)
            case _ => Future.failed(new IllegalStateException("No session found"))
          }
          result <- tournamentService.submitMatchResult(tournamentMatch, winnerData.winner)
          _ <- recordMatchSmurfs(tournamentMatch,
            firstParticipantSmurfs = winnerData.smurfsFirstParticipant.toSet,
            secondParticipantSmurfs = winnerData.smurfsSecondParticipant.toSet)
          _ <- persistMetaDataSessionFiles(currentSession)
          _ = uploadSessionService.finalizeSession(currentSession)
        }yield{
          Ok(views.html.index(Some(request.identity)))
        }
      })
  }


}
