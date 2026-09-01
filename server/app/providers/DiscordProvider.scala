package providers

import play.api.libs.json.JsValue
import play.silhouette.api.LoginInfo
import play.silhouette.api.util.HTTPLayer
import play.silhouette.impl.exceptions.ProfileRetrievalException
import play.silhouette.impl.providers._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 * Base trait for the Discord OAuth2 Provider.
 *
 * @see https://docs.discord.com/developers/topics/oauth2
 */
trait BaseDiscordProvider extends OAuth2Provider {

  /**
   * The content type to parse.
   */
  override type Content = JsValue

  /**
   * The provider ID.
   */
  override val id: String = DiscordProvider.ID

  /**
   * API endpoint used to retrieve profile information.
   */
  override protected val urls = Map(
    "api" -> settings.apiURL.getOrElse(DiscordProvider.API)
  )

  /**
   * Builds the social profile.
   *
   * @param authInfo The auth info received from the provider.
   * @return On success the build social profile, otherwise a failure.
   */
  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api"))
      .withHttpHeaders("Authorization" -> s"Bearer ${authInfo.accessToken}")
      .get()
      .flatMap { response =>
        response.status match {
          case 200 => profileParser.parse(response.json, authInfo)
          case status =>
            val message = (response.json \ "message").asOpt[String].getOrElse(response.body)
            Future.failed(new ProfileRetrievalException(DiscordProvider.APIError.format(id, status, message)))
        }
      }
      .recoverWith {
        case e: ProfileRetrievalException => Future.failed(e)
        case e => Future.failed(new ProfileRetrievalException(DiscordProvider.UnspecifiedProfileError.format(id, e)))
      }
  }
}

/**
 * The profile parser for the Discord provider.
 */
class DiscordProfileParser extends SocialProfileParser[JsValue, CommonSocialProfile, OAuth2Info] {

  /**
   * Parses the social profile.
   *
   * @see https://docs.discord.com/developers/docs/resources/user#user-object
   *
   * @param json     The content returned from the provider.
   * @param authInfo The auth info to query the provider again for additional data.
   * @return The social profile from given result.
   */
  override def parse(json: JsValue, authInfo: OAuth2Info): Future[CommonSocialProfile] = {
    Future.successful {
      val userId = (json \ "id").as[String]
      val username = (json \ "username").asOpt[String]
      val globalName = (json \ "global_name").asOpt[String]

      // global_name is the display name set by the user; fall back to username
      val fullName = globalName.orElse(username)

      // Avatar hash may be animated (starts with "a_"); Discord CDN needs .gif in that case
      val avatarURL = (json \ "avatar").asOpt[String].map { hash =>
        val extension = if (hash.startsWith("a_")) "gif" else "png"
        s"https://cdn.discordapp.com/avatars/$userId/$hash.$extension"
      }

      CommonSocialProfile(
        loginInfo = LoginInfo(DiscordProvider.ID, userId),
        firstName = None,
        lastName = None,
        fullName = fullName,
        email = (json \ "email").asOpt[String],
        avatarURL = avatarURL
      )
    }
  }
}

/**
 * The Discord OAuth2 Provider.
 *
 * @param httpLayer    The HTTP layer implementation.
 * @param stateHandler The social state handler implementation.
 * @param settings     The provider settings.
 */
class DiscordProvider @Inject()(
  protected val httpLayer: HTTPLayer,
  protected val stateHandler: SocialStateHandler,
  val settings: OAuth2Settings
)(implicit val ec: ExecutionContext) extends BaseDiscordProvider with CommonSocialProfileBuilder {

  /**
   * The type of this class.
   */
  override type Self = DiscordProvider

  /**
   * The profile parser implementation.
   */
  override val profileParser = new DiscordProfileParser

  /**
   * Creates a new instance with new settings.
   *
   * @param f A function which gets the settings passed and returns different settings.
   * @return An instance of this provider with new settings.
   */
  override def withSettings(f: OAuth2Settings => OAuth2Settings): DiscordProvider = {
    new DiscordProvider(httpLayer, stateHandler, f(settings))(ec)
  }
}

/**
 * The companion object.
 */
object DiscordProvider {

  /**
   * The provider ID.
   */
  val ID = "discord"

  /**
   * The URL to fetch the current user's profile.
   */
  val API = "https://discord.com/api/users/@me"

  /**
   * Error messages.
   */
  val UnspecifiedProfileError = "[Silhouette][%s] Error retrieving Discord profile. Cause: %s"
  val APIError = "[Silhouette][%s] Discord API returned status %s: %s"
}
