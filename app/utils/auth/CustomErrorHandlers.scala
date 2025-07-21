package utils.auth

import javax.inject.Inject
import play.api.mvc.{RequestHeader, Result, Results}
import play.silhouette.api.actions.{SecuredErrorHandler, UnsecuredErrorHandler}
import play.api.i18n.MessagesApi
import controllers.routes

import scala.concurrent.Future

/**
 * Custom error handler for secured actions (authenticated users only).
 * When a user is not authenticated or not authorized, redirect to index with flash messages.
 */
class CustomSecuredErrorHandler @Inject()(messagesApi: MessagesApi) extends SecuredErrorHandler {
  
  /**
   * Called when a user is not authenticated (no login).
   * Redirects to index page with flash message indicating login is required.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  override def onNotAuthenticated(implicit request: RequestHeader): Future[Result] = {
    Future.successful(
      Results.Redirect(routes.HomeController.index())
        .flashing("error" -> "You must be logged in to access this page.")
    )
  }

  /**
   * Called when a user is authenticated but not authorized (logged in but insufficient permissions).
   * Redirects to index page with flash message indicating insufficient permissions.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  override def onNotAuthorized(implicit request: RequestHeader): Future[Result] = {
    Future.successful(
      Results.Redirect(routes.HomeController.index())
        .flashing("error" -> "You don't have permission to access this page.")
    )
  }
}

/**
 * Custom error handler for unsecured actions (non-authenticated users only).
 * When an authenticated user tries to access a page meant for non-authenticated users only,
 * redirect to index with flash message.
 */
class CustomUnsecuredErrorHandler @Inject()(messagesApi: MessagesApi) extends UnsecuredErrorHandler {
  
  /**
   * Called when a user is authenticated but trying to access an unsecured-only endpoint
   * (e.g., login page when already logged in).
   * Redirects to index page.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  override def onNotAuthorized(implicit request: RequestHeader): Future[Result] = {
    Future.successful(
      Results.Redirect(routes.HomeController.index())
    )
  }
}
