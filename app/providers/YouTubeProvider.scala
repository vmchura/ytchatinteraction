package providers

import com.typesafe.config.Config
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.ws.WSClient
import play.silhouette.api.LoginInfo
import play.silhouette.api.util.HTTPLayer
import play.silhouette.impl.exceptions.ProfileRetrievalException
import play.silhouette.impl.providers._

import scala.concurrent.{ExecutionContext, Future}

/**
 * YouTube OAuth2 Provider for Silhouette.
 *
 * @param httpLayer    The HTTP layer implementation.
 * @param stateHandler The social state handler implementation.
 * @param settings     The provider settings.
 * @param ec           The execution context.
 */
class YouTubeProvider(
  protected val httpLayer: HTTPLayer,
  protected val stateHandler: SocialStateHandler,
  val settings: OAuth2Settings
)(implicit val ec: ExecutionContext) extends OAuth2Provider {

  /**
   * The provider ID.
   */
  override val id: String = YouTubeProvider.ID

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: OAuth2Info): Future[SocialProfile] = {
    // First, fetch the user's YouTube channel information
    fetchChannelInfo(authInfo).flatMap { channelInfo =>
      // Then, fetch additional user info (email, etc) from the userinfo endpoint
      fetchUserInfo(authInfo).map { userInfo =>
        buildUserProfile(channelInfo, userInfo)
      }
    }
  }

  /**
   * Fetches the user's YouTube channel information.
   *
   * @param authInfo The OAuth2 authentication info.
   * @return The JSON response containing channel information.
   */
  private def fetchChannelInfo(authInfo: OAuth2Info): Future[JsValue] = {
    httpLayer.url("https://www.googleapis.com/youtube/v3/channels")
      .withHttpHeaders("Authorization" -> s"Bearer ${authInfo.accessToken}")
      .withQueryStringParameters(
        "part" -> "snippet,contentDetails,statistics",
        "mine" -> "true"
      )
      .get()
      .flatMap { response =>
        response.status match {
          case 200 =>
            // Check if response contains items
            val json = response.json
            val items = (json \ "items").asOpt[Seq[JsValue]]
            items.filter(_.nonEmpty) match {
              case Some(channelItems) => Future.successful(channelItems.head)
              case None => Future.failed(new ProfileRetrievalException("No YouTube channel found for the authenticated user"))
            }
          case status => 
            Future.failed(new ProfileRetrievalException(s"YouTube API returned status $status: ${response.body}"))
        }
      }
  }

  /**
   * Fetches additional user information from Google's userinfo endpoint.
   *
   * @param authInfo The OAuth2 authentication info.
   * @return The JSON response containing user information.
   */
  private def fetchUserInfo(authInfo: OAuth2Info): Future[JsValue] = {
    httpLayer.url("https://www.googleapis.com/oauth2/v3/userinfo")
      .withHttpHeaders("Authorization" -> s"Bearer ${authInfo.accessToken}")
      .get()
      .flatMap { response =>
        response.status match {
          case 200 => Future.successful(response.json)
          case status => 
            Future.failed(new ProfileRetrievalException(s"Google UserInfo API returned status $status: ${response.body}"))
        }
      }
  }

  /**
   * Builds a user profile from channel and user information.
   *
   * @param channelInfo The YouTube channel information.
   * @param userInfo The Google user information.
   * @return A CommonSocialProfile instance.
   */
  private def buildUserProfile(channelInfo: JsValue, userInfo: JsValue): SocialProfile = {
    // Extract essential information
    val channelId = (channelInfo \ "id").as[String]
    val snippet = (channelInfo \ "snippet").as[JsObject]
    val channelTitle = (snippet \ "title").asOpt[String]
    val thumbnailUrl = (snippet \ "thumbnails" \ "default" \ "url").asOpt[String]
    
    // Extract email information (if available)
    val email = (userInfo \ "email").asOpt[String]
    val verifiedEmail = (userInfo \ "verified_email").asOpt[Boolean].getOrElse(false)
    val safeEmail = if (verifiedEmail) email else None
    
    // Extract name information
    val firstName = (userInfo \ "given_name").asOpt[String]
    val lastName = (userInfo \ "family_name").asOpt[String]
    
    // Fallback to channel title if no name available
    val finalFirstName = firstName.orElse(channelTitle)
    val finalFullName = {
      (firstName, lastName) match {
        case (Some(first), Some(last)) => Some(s"$first $last")
        case _ => channelTitle
      }
    }
    
    // Build the profile
    CommonSocialProfile(
      loginInfo = LoginInfo(id, channelId),
      firstName = finalFirstName,
      lastName = lastName,
      fullName = finalFullName,
      email = safeEmail,
      avatarURL = thumbnailUrl
    )
  }
}

/**
 * The companion object.
 */
object YouTubeProvider {
  /**
   * The provider ID.
   */
  val ID = "youtube"
}

