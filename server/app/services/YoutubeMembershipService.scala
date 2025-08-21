package services

import javax.inject.{Inject, Singleton}
import play.api.libs.ws.WSClient
import play.api.Configuration
import play.api.libs.json._
import models.repository.OAuth2InfoRepository
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class YoutubeMembershipService @Inject()(
                                          ws: WSClient,
                                          config: Configuration,
                                          oauth2InfoRepository: OAuth2InfoRepository,
                                          tokenRefreshService: OAuth2TokenRefreshService
                                        )(implicit ec: ExecutionContext) {

  private val baseUrl = "https://www.googleapis.com/youtube/v3"

  def isSubscribedToChannel(userChannelId: String, targetChannelId: String): Future[Boolean] = {
    tokenRefreshService.refreshTokenIfNeeded(userChannelId).flatMap {
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
            response.status match {
              case 200 =>
                val items = (response.json \ "items").as[JsArray]
                items.value.nonEmpty
              case 401 =>
                println(s"Authentication failed even after token refresh for channel $userChannelId")
                false
              case _ =>
                println(s"YouTube API error: ${response.status} - ${response.body}")
                false
            }
          }
      case None =>
        println(s"Could not obtain valid token for channel $userChannelId")
        Future.successful(false)
    }.recover {
      case ex =>
        println(s"Error checking subscription: ${ex.getMessage}")
        false
    }
  }
}