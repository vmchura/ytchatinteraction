package providers

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
 * Base trait for YouTube OAuth2 Provider.
 */
trait BaseYouTubeProvider extends OAuth2Provider {

  /**
   * The content type to parse.
   */
  override type Content = JsValue

  /**
   * The provider ID.
   */
  override val id: String = YouTubeProvider.ID

  /**
   * API endpoints used to retrieve profile information.
   */
  protected val urls = Map(
    "channels" -> "https://www.googleapis.com/youtube/v3/channels",
    "userinfo" -> "https://www.googleapis.com/oauth2/v3/userinfo"
  )

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    // First, fetch the user's YouTube channel information
    fetchChannelInfo(authInfo).flatMap { channelInfo =>
      // Then, fetch additional user info (email, etc) from the userinfo endpoint
        profileParser.parse(channelInfo, authInfo)
    }.recoverWith {
      case e: ProfileRetrievalException => Future.failed(e)
      case e => Future.failed(new ProfileRetrievalException(YouTubeProvider.UnspecifiedProfileError.format(id, e)))
    }
  }

  /**
   * Fetches the user's YouTube channel information.
   *
   * @param authInfo The OAuth2 authentication info.
   * @return The JSON response containing channel information.
   */
  protected def fetchChannelInfo(authInfo: OAuth2Info): Future[JsValue] = {
    httpLayer.url(urls("channels"))
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
              case None => Future.failed(new ProfileRetrievalException(YouTubeProvider.NoChannelFoundError.format(id)))
            }
          case status => 
            Future.failed(new ProfileRetrievalException(YouTubeProvider.ChannelAPIError.format(id, status, response.body)))
        }
      }
  }

  /**
   * Fetches additional user information from Google's userinfo endpoint.
   *
   * @param authInfo The OAuth2 authentication info.
   * @return The JSON response containing user information.
   */
  protected def fetchUserInfo(authInfo: OAuth2Info): Future[JsValue] = {
    httpLayer.url(urls("userinfo"))
      .withHttpHeaders("Authorization" -> s"Bearer ${authInfo.accessToken}")
      .get()
      .flatMap { response =>
        response.status match {
          case 200 => Future.successful(response.json)
          case status => 
            Future.failed(new ProfileRetrievalException(YouTubeProvider.UserInfoAPIError.format(id, status, response.body)))
        }
      }
  }
}

/**
 * The profile parser for YouTube provider.
 */
class YouTubeProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @param channelInfo The channel information returned from the API.
   * @param userInfo The user information returned from the API.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  def parse(channelInfo: JsValue, authInfo: OAuth2Info): Future[CommonSocialProfile] = {
    Future.successful {
      // Extract essential information
      val channelId = (channelInfo \ "id").as[String]
      val snippet = (channelInfo \ "snippet").as[JsObject]
      val channelTitle = (snippet \ "title").asOpt[String]
      val thumbnailUrl = (snippet \ "thumbnails" \ "default" \ "url").asOpt[String]
      
      // Fallback to channel title if no name available
      
      // Build the profile
      CommonSocialProfile(
        loginInfo = LoginInfo(YouTubeProvider.ID, channelId),
        firstName = None,
        lastName = None,
        fullName = channelTitle,
        email = None,
        avatarURL = thumbnailUrl
      )
    }
  }
}

/**
 * The YouTube OAuth2 Provider.
 *
 * @param httpLayer    The HTTP layer implementation.
 * @param stateHandler The social state handler implementation.
 * @param settings     The provider settings.
 */
class YouTubeProvider @Inject()(
  protected val httpLayer: HTTPLayer,
  protected val stateHandler: SocialStateHandler,
  val settings: OAuth2Settings
)(implicit val ec: ExecutionContext) extends BaseYouTubeProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = YouTubeProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new YouTubeProfileParser

  /**
   * Creates a new instance with new settings.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of this provider with new settings.
   */
  override def withSettings(f: OAuth2Settings => OAuth2Settings): YouTubeProvider = {
    new YouTubeProvider(httpLayer, stateHandler, f(settings))(ec)
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
  
  /**
   * Error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Cause: %s"
  val NoChannelFoundError = "[Silhouette][%s] No YouTube channel found for the authenticated user"
  val ChannelAPIError = "[Silhouette][%s] YouTube API returned status %s: %s"
  val UserInfoAPIError = "[Silhouette][%s] Google UserInfo API returned status %s: %s"
}
