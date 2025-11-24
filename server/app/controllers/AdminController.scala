package controllers

import modules.DefaultEnv
import play.api.mvc.{Action, AnyContent}
import play.silhouette.api.Silhouette
import services.{AnalyticalReplayService, UserService}
import utils.auth.WithAdmin

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 * Example controller showing how to use admin authorization.
 * This controller contains admin-only actions.
 */
class AdminController @Inject()(
  components: DefaultSilhouetteControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  userService: UserService,
  analyticalReplayService: AnalyticalReplayService
)(implicit ec: ExecutionContext) extends SilhouetteController(components) {

  /**
   * Admin dashboard - only accessible by admin users.
   */
  def adminDashboard: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    Future.successful {
      Ok(views.html.admin.dashboard(request.identity))
    }
  }
  def reRunAnalysis(tournamentId: Long, challongeMatchID: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    analyticalReplayService.analyticalProcessMatch(tournamentId, challongeMatchID).map(_ => Ok)
  }
}
