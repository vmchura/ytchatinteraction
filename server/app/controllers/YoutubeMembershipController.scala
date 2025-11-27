package controllers

import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import play.silhouette.api.Silhouette
import modules.DefaultEnv
import services.{ContentCreatorChannelService, YoutubeMembershipService}
import models.repository.YtUserRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class YoutubeMembershipController @Inject()(
                                             components: ControllerComponents,
                                             silhouette: Silhouette[DefaultEnv],
                                             youtubeMembershipService: YoutubeMembershipService,
                                             contentCreatorChannelService: ContentCreatorChannelService,
                                             ytUserRepository: YtUserRepository
                                           )(implicit ec: ExecutionContext) extends AbstractController(components) with I18nSupport {

  def getSubscribedChannels(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    ytUserRepository.getByUserId(request.identity.userId).flatMap { ytUsers =>
      ytUsers.headOption match {
        case Some(ytUser) =>
          contentCreatorChannelService.getAllContentCreatorChannels().flatMap { channels =>
            Future.traverse(channels) { channel =>
              youtubeMembershipService.isSubscribedToChannel(ytUser.userChannelId, channel.youtubeChannelId).map { isSubscribed =>
                if (isSubscribed) Some(channel) else None
              }
            }.map { results =>
              Ok(views.html.youtubeMemberships(request.identity, results.flatten))
            }
          }
        case None =>
          Future.successful(NotFound("No YouTube account linked"))
      }
    }.recover {
      case ex: Exception =>
        BadRequest(s"Error: ${ex.getMessage}")
    }
  }
}