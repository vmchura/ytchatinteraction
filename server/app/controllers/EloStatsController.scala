package controllers

import models.repository.{EloRepository, UserAliasRepository}
import javax.inject._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EloStatsController @Inject() (
    components: DefaultSilhouetteControllerComponents,
    eloRepository: EloRepository,
    userAliasRepository: UserAliasRepository
)(implicit ec: ExecutionContext)
    extends SilhouetteController(components) {

  def viewStats(userId: Long): Action[AnyContent] =
    silhouette.SecuredAction.async { implicit request =>
      for {
        currentElos <- eloRepository.getAllElosByUserId(userId)
        eloLogs <- eloRepository.getAllLogsByUserId(userId)
        userAliasOpt <- userAliasRepository.getCurrentAlias(userId)
      } yield {
        Ok(
          views.html.eloStats(
            request.identity,
            userId,
            userAliasOpt.getOrElse("Unknown"),
            currentElos,
            eloLogs
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
