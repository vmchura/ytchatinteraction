package services

import javax.inject.{Inject, Singleton}
import play.api.libs.ws.WSClient
import play.api.Configuration
import play.api.libs.json._
import models.repository.OAuth2InfoRepository
import scala.concurrent.{ExecutionContext, Future}

/**
 * Service for checking YouTube paid memberships/sponsorships to specific channels.
 * This ONLY checks for PAID memberships, not regular subscriptions.
 */
@Singleton
class YoutubeMembershipService @Inject()(
                                          ws: WSClient,
                                          config: Configuration,
                                          oauth2InfoRepository: OAuth2InfoRepository
                                        )(implicit ec: ExecutionContext) {

  private val baseUrl = "https://www.googleapis.com/youtube/v3"

  def isSubscribedToChannel(userChannelId: String, targetChannelId: String): Future[Boolean] = {
    oauth2InfoRepository.getByChannelId(userChannelId).flatMap {
      case Some(oauth2Info) =>
        val url = s"$baseUrl/subscriptions"

        ws.url(url)
          .withHttpHeaders("Authorization" -> s"Bearer ${oauth2Info.accessToken}")
          .withQueryStringParameters(
            "part" -> "snippet",
            "mine" -> "true",
            "forChannelId" -> targetChannelId
          )
          .get()
          .map { response =>
            println(response)
            response.status match {
              case 200 =>
                val items = (response.json \ "items").as[JsArray]
                items.value.nonEmpty // true if subscribed, false if not
              case _ =>
                false
            }
          }
      case None =>
        Future.successful(false)
    }.recover {
      case _ => false
    }
  }
}