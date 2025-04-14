package models

import play.api.libs.json._
import java.time.Instant

/**
 * Entity for storing OAuth2 tokens and related information.
 *
 * @param id The unique ID of the token.
 * @param userChannelId The YouTube channel ID this token belongs to.
 * @param accessToken The OAuth2 access token.
 * @param tokenType The type of token (usually "Bearer").
 * @param expiresIn The token expiration in seconds.
 * @param refreshToken The refresh token for obtaining new access tokens.
 * @param createdAt When this token was created.
 * @param updatedAt When this token was last updated.
 */
case class OAuth2Info(
  id: Option[Long] = None,
  userChannelId: String,
  accessToken: String,
  tokenType: Option[String] = None,
  expiresIn: Option[Int] = None,
  refreshToken: Option[String] = None,
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now()
) {
  /**
   * Convert to Silhouette's OAuth2Info object.
   * This makes it easy to use our database model with Silhouette.
   */
  def toSilhouetteOAuth2Info: play.silhouette.impl.providers.OAuth2Info = {
    play.silhouette.impl.providers.OAuth2Info(
      accessToken = accessToken,
      tokenType = tokenType,
      expiresIn = expiresIn,
      refreshToken = refreshToken
    )
  }
}

object OAuth2Info {
  implicit val instantFormat: Format[Instant] = new Format[Instant] {
    def reads(json: JsValue): JsResult[Instant] = json match {
      case JsString(s) => JsSuccess(Instant.parse(s))
      case JsNumber(n) => JsSuccess(Instant.ofEpochMilli(n.toLong))
      case _ => JsError("Instant format error")
    }
    def writes(instant: Instant): JsValue = JsString(instant.toString)
  }

  implicit val oauth2InfoFormat: OFormat[OAuth2Info] = Json.format[OAuth2Info]

  /**
   * Create an OAuth2Info instance from a Silhouette OAuth2Info.
   *
   * @param silhouetteOAuth2Info The Silhouette OAuth2Info.
   * @param userChannelId The YouTube channel ID this token belongs to.
   * @return An OAuth2Info instance.
   */
  def fromSilhouetteOAuth2Info(
    silhouetteOAuth2Info: play.silhouette.impl.providers.OAuth2Info, 
    userChannelId: String
  ): OAuth2Info = {
    OAuth2Info(
      userChannelId = userChannelId,
      accessToken = silhouetteOAuth2Info.accessToken,
      tokenType = silhouetteOAuth2Info.tokenType,
      expiresIn = silhouetteOAuth2Info.expiresIn,
      refreshToken = silhouetteOAuth2Info.refreshToken
    )
  }
}
