package controllers

import models.ContentCreatorChannel
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.silhouette.api.Silhouette
import modules.DefaultEnv
import services.ContentCreatorChannelService
import utils.auth.WithAdmin

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Controller for managing content creator channels (admin only).
 */
@Singleton
class ContentCreatorChannelController @Inject()(
                                                 components: ControllerComponents,
                                                 silhouette: Silhouette[DefaultEnv],
                                                 contentCreatorChannelService: ContentCreatorChannelService
                                               )(implicit ec: ExecutionContext) extends AbstractController(components) with I18nSupport {

  /**
   * Displays the content creator channels management page (admin only).
   */
  def index(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    contentCreatorChannelService.getAllContentCreatorChannels().map { channels =>
      Ok(views.html.admin.contentCreatorChannels(request.identity, channels))
    }
  }

  /**
   * Displays the form to create a new content creator channel (admin only).
   */
  def create(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()) { implicit request =>
    Ok(views.html.admin.createContentCreatorChannel(request.identity))
  }

  /**
   * Handles the creation of a new content creator channel (admin only).
   * Expects a YouTube channel URL like: https://www.youtube.com/@RemastrTV
   */
  def store(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    val formData = request.body.asFormUrlEncoded.getOrElse(Map.empty)

    val channelUrlOpt = formData.get("channelUrl").flatMap(_.headOption)

    channelUrlOpt match {
      case Some(channelUrl) =>
        contentCreatorChannelService.createContentCreatorChannelFromUrl(channelUrl.trim).map {
          case Right(channel) =>
            Redirect(routes.ContentCreatorChannelController.index())
              .flashing("success" -> s"Content creator channel '${channel.youtubeChannelName}' created successfully")
          case Left(error) =>
            Redirect(routes.ContentCreatorChannelController.create())
              .flashing("error" -> error)
        }
      case None =>
        Future.successful(
          Redirect(routes.ContentCreatorChannelController.create())
            .flashing("error" -> "YouTube Channel URL is required")
        )
    }
  }




  /**
   * Toggles the active status of a content creator channel (admin only).
   */
  def toggleActive(id: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    contentCreatorChannelService.getContentCreatorChannel(id).flatMap {
      case Some(channel) =>
        val newStatus = !channel.isActive
        contentCreatorChannelService.setChannelActiveStatus(id, newStatus).map {
          case Right(updatedChannel) =>
            val statusText = if (newStatus) "activated" else "deactivated"
            Redirect(routes.ContentCreatorChannelController.index())
              .flashing("success" -> s"Content creator channel '${updatedChannel.youtubeChannelName}' $statusText successfully")
          case Left(error) =>
            Redirect(routes.ContentCreatorChannelController.index())
              .flashing("error" -> error)
        }
      case None =>
        Future.successful(NotFound("Content creator channel not found"))
    }
  }


}