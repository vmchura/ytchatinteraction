package controllers

import play.api.i18n.I18nSupport

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import utils.auth.WithAdmin

@Singleton
class PollsViewController @Inject()(components: DefaultSilhouetteControllerComponents) extends SilhouetteController(components) with I18nSupport with RequestMarkerContext {
  
  def viewPolls: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()) { implicit request =>
    Redirect(controllers.routes.Assets.versioned("polls_obs.html"))
  }
}
