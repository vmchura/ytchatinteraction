package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

/**
 * Controller for displaying the polls view
 */
@Singleton
class PollsViewController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  
  /**
   * Redirects to the static polls HTML page
   */
  def viewPolls: Action[AnyContent] = Action { implicit request =>
    Redirect(controllers.routes.Assets.versioned("polls.html"))
  }
}
