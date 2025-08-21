package services

import javax.inject.{Inject, Singleton}
import play.api.libs.ws.WSClient
import play.api.Configuration
import play.api.libs.json._
import models.repository.OAuth2InfoRepository
import models.OAuth2Info
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant
import play.silhouette.api.LoginInfo
import play.api.libs.ws.WSBodyWritables._

@Singleton
class OAuth2TokenRefreshService @Inject()(
                                           ws: WSClient,
                                           config: Configuration,
                                           oauth2InfoRepository: OAuth2InfoRepository
                                         )(implicit ec: ExecutionContext) {

  private val tokenUrl = "https://oauth2.googleapis.com/token"

  def refreshTokenIfNeeded(userChannelId: String): Future[Option[OAuth2Info]] = {
    oauth2InfoRepository.getByChannelId(userChannelId).flatMap {
      case Some(oauth2Info) =>
        if (isTokenExpired(oauth2Info)) {
          refreshToken(oauth2Info)
        } else {
          Future.successful(Some(oauth2Info))
        }
      case None =>
        Future.successful(None)
    }
  }

  private def isTokenExpired(oauth2Info: OAuth2Info): Boolean = {
    oauth2Info.expiresIn match {
      case Some(expiresIn) =>
        val expirationTime = oauth2Info.updatedAt.plusSeconds(expiresIn.toLong)
        val bufferTime = 300 // 5-minute buffer to refresh before actual expiry
        Instant.now().isAfter(expirationTime.minusSeconds(bufferTime))
      case None =>
        // If no expiration info, assume expired and try refresh
        true
    }
  }

  private def refreshToken(oauth2Info: OAuth2Info): Future[Option[OAuth2Info]] = {
    oauth2Info.refreshToken match {
      case Some(refreshToken) =>
        val clientId = config.get[String]("silhouette.youtube.clientID")
        val clientSecret = config.get[String]("silhouette.youtube.clientSecret")

        ws.url(tokenUrl)
          .withHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
          .post(Map(
            "grant_type" -> Seq("refresh_token"),
            "refresh_token" -> Seq(refreshToken),
            "client_id" -> Seq(clientId),
            "client_secret" -> Seq(clientSecret)
          ))
          .flatMap { response =>
            response.status match {
              case 200 =>
                val json = response.json
                val newAccessToken = (json \ "access_token").as[String]
                val expiresIn = (json \ "expires_in").asOpt[Int]
                val newRefreshToken = (json \ "refresh_token").asOpt[String]

                val updatedOAuth2Info = oauth2Info.copy(
                  accessToken = newAccessToken,
                  expiresIn = expiresIn,
                  refreshToken = newRefreshToken.orElse(oauth2Info.refreshToken), // Keep old if not provided
                  updatedAt = Instant.now()
                )

                // Update in database
                val loginInfo = LoginInfo("youtube", oauth2Info.userChannelId)
                oauth2InfoRepository.update(loginInfo, updatedOAuth2Info.toSilhouetteOAuth2Info)
                  .map(_ => Some(updatedOAuth2Info))

              case 400 =>
                // Refresh token might be expired or invalid
                println(s"Refresh token expired for channel ${oauth2Info.userChannelId}")
                Future.successful(None)
              case _ =>
                println(s"Token refresh failed with status ${response.status}: ${response.body}")
                Future.successful(None)
            }
          }
      case None =>
        println(s"No refresh token available for channel ${oauth2Info.userChannelId}")
        Future.successful(None)
    }
  }
}
