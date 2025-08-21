package controllers

import models.ContentCreatorChannel
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.silhouette.api.Silhouette
import modules.DefaultEnv
import services.ContentCreatorChannelService

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
  def index(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    // TODO: Add admin check here when admin system is implemented
    contentCreatorChannelService.getAllContentCreatorChannels().map { channels =>
      // TODO: Replace with proper view
      Ok(s"Content Creator Channels: ${channels.length} channels")
    }
  }

  /**
   * Displays the form to create a new content creator channel (admin only).
   */
  def create(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    // TODO: Add admin check here when admin system is implemented
    Future.successful {
      // TODO: Replace with proper view
      Ok("Create Content Creator Channel Form")
    }
  }

  /**
   * Handles the creation of a new content creator channel (admin only).
   */
  def store(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    // TODO: Add admin check here when admin system is implemented
    val formData = request.body.asFormUrlEncoded.getOrElse(Map.empty)

    val youtubeChannelIdOpt = formData.get("youtubeChannelId").flatMap(_.headOption)
    val youtubeChannelNameOpt = formData.get("youtubeChannelName").flatMap(_.headOption)

    (youtubeChannelIdOpt, youtubeChannelNameOpt) match {
      case (Some(channelId), Some(channelName)) =>
        contentCreatorChannelService.createContentCreatorChannel(channelId.trim, channelName.trim).map {
          case Right(channel) =>
            Redirect(routes.ContentCreatorChannelController.index())
              .flashing("success" -> s"Content creator channel '${channel.youtubeChannelName}' created successfully")
          case Left(error) =>
            Redirect(routes.ContentCreatorChannelController.create())
              .flashing("error" -> error)
        }
      case _ =>
        Future.successful(
          Redirect(routes.ContentCreatorChannelController.create())
            .flashing("error" -> "YouTube Channel ID and Channel Name are required")
        )
    }
  }




  /**
   * Toggles the active status of a content creator channel (admin only).
   */
  def toggleActive(id: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    // TODO: Add admin check here when admin system is implemented
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