package controllers

import models.repository.{
  AnalyticalResultRepository,
  UploadedFileRepository,
  UserAliasRepository
}
import models._

import javax.inject.*
import play.api.mvc.*
import play.api.data.*
import play.api.data.Forms.*
import services._
import evolutioncomplete.GameStateShared.ValidGame
import evolutioncomplete.ParticipantShared
import evolutioncomplete.WinnerShared.*
import forms.Forms
import models.MatchStatus.{InProgress, Pending}

import scala.concurrent.{ExecutionContext, Future}
import utils.auth.WithAdmin

@Singleton
class MatchResultController @Inject() (
    components: DefaultSilhouetteControllerComponents,
    tournamentService: services.TournamentService,
    userSmurfService: services.UserSmurfService,
    uploadSessionService: services.TournamentUploadSessionService,
    uploadedFileRepository: UploadedFileRepository,
    analyticalReplayService: AnalyticalReplayService,
    analyticalResultRepository: AnalyticalResultRepository,
    userAliasRepository: UserAliasRepository
)(implicit ec: ExecutionContext)
    extends SilhouetteController(components) {

  private def recordMatchSmurfs(
      tournamentMatch: TournamentMatch,
      firstParticipantSmurfs: Set[String],
      secondParticipantSmurfs: Set[String]
  ): Future[Seq[TournamentUserSmurf]] = {
    userSmurfService.recordMatchSmurfs(
      tournamentMatch.matchId,
      tournamentMatch.tournamentId,
      ParticipantShared(
        tournamentMatch.firstUserId,
        "",
        firstParticipantSmurfs
      ),
      ParticipantShared(
        tournamentMatch.secondUserId,
        "",
        secondParticipantSmurfs
      )
    )
  }

  private def persistMetaDataSessionFiles(
      session: TournamentSession
  ): Future[Int] = {
    Future
      .sequence(
        session.uploadState.games
          .filter {
            case ValidGame(_, _, _, _, _, _) => true
            case _                        => false
          }
          .flatMap {
            case ValidGame(_, _, _, hash, _, _) => Some(hash)
            case _                           => None
          }
          .flatMap(hash =>
            session.hash2StoreInformation.get(hash).map((hash, _)).map {
              case (hash, storedInfo) =>
                val uploadedFile = models.UploadedFile(
                  userId = session.userId,
                  tournamentId = session.tournamentId,
                  matchId = session.matchId,
                  sha256Hash = hash,
                  originalName = storedInfo.originalFileName,
                  relativeDirectoryPath = storedInfo.storedPath,
                  savedFileName = storedInfo.storedFileName,
                  uploadedAt = storedInfo.storedAt
                )

                uploadedFileRepository.create(uploadedFile).map { createdFile =>
                  logger.info(
                    s"Saved file record to database: ID ${createdFile.id}, SHA256: ${createdFile.sha256Hash}"
                  )
                  1
                }

            }
          )
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
            tournamentMatch <- tournamentMatchOption match {
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
              .submitMatchResult(tournamentMatch, winnerData.winner)
            _ <- recordMatchSmurfs(
              tournamentMatch,
              firstParticipantSmurfs = winnerData.smurfsFirstParticipant.toSet,
              secondParticipantSmurfs = winnerData.smurfsSecondParticipant.toSet
            )
            _ <- persistMetaDataSessionFiles(currentSession)
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
