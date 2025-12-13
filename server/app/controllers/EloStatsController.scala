package controllers

import models.repository.{EloRepository, UserAliasRepository}

import javax.inject.*
import play.api.mvc.*
import services.MatchHistoryService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EloStatsController @Inject() (
    components: DefaultSilhouetteControllerComponents,
    eloRepository: EloRepository,
    userAliasRepository: UserAliasRepository,
    matchHistoryService: MatchHistoryService
)(implicit ec: ExecutionContext)
    extends SilhouetteController(components) {

  def viewStats(userId: Long): Action[AnyContent] =
    silhouette.SecuredAction.async { implicit request =>
      for {
        currentElos <- eloRepository.getAllElosByUserId(userId)
        eloLogs <- eloRepository.getAllLogsByUserId(userId)
        userAliasOpt <- userAliasRepository.getCurrentAlias(userId)
        recentMatches <- matchHistoryService.recentMatches(userId)
      } yield {
        Ok(
          views.html.eloStats(
            request.identity,
            userId,
            userAliasOpt.getOrElse("Unknown"),
            currentElos,
            eloLogs,
            recentMatches
          )
        )
      }
    }

  def globalRankings(): Action[AnyContent] = silhouette.SecuredAction.async {
    implicit request =>
      eloRepository.getAllElosWithUserNames().map { allElos =>
        val sortedElos = allElos.sortBy(_.elo)(Ordering[Int].reverse)
        Ok(views.html.eloGlobalRankings(request.identity, sortedElos))
      }
  }
}
