package controllers

import models.repository.{LoginInfoRepository, OAuth2InfoRepository, UserRepository, YtUserRepository}
import javax.inject.Inject
import models.{User, YtUser}
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, Cookie, Request, Result}
import play.silhouette.api.Authenticator.Implicits._
import play.silhouette.api.exceptions.ProviderException
import play.silhouette.api.repositories.AuthInfoRepository
import play.silhouette.api.{LoginEvent, LogoutEvent, Silhouette}
import play.silhouette.impl.providers.{CommonSocialProfileBuilder, SocialProviderRegistry}
import providers.YouTubeProvider
import play.silhouette.impl.providers.CommonSocialProfile
import services.UserService

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import play.api.libs.json.Json
import play.silhouette.api.util.Credentials
import play.silhouette.api.LoginInfo
import modules.DefaultEnv

/**
 * The authentication controller.
 */
class AuthController @Inject()(
  components: ControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  userService: UserService,
  authInfoRepository: AuthInfoRepository,
  socialProviderRegistry: SocialProviderRegistry,
  userRepository: UserRepository,
  ytUserRepository: YtUserRepository,
  loginInfoRepository: LoginInfoRepository,
  oauth2InfoRepository: OAuth2InfoRepository
)(implicit ex: ExecutionContext) extends AbstractController(components) with I18nSupport {

  /**
   * Displays the login page with YouTube authentication button.
   *
   * @return The result to display.
   */
  def login: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.login())
  }

  /**
   * Handles the YouTube authentication.
   *
   * @return The result to display.
   */
  def authenticate(provider: String) = Action.async { implicit request =>
    (socialProviderRegistry.get[YouTubeProvider] match {
      case Some(p) => p.authenticate()
      case None => Future.failed(new ProviderException(s"Cannot authenticate with unknown provider $provider"))
    }).flatMap {
      case Left(result) => Future.successful(result)
      case Right(authInfo) => for {
        profile <- socialProviderRegistry.get[YouTubeProvider].get.retrieveProfile(authInfo)
        user <- loginInfoRepository.findUser(profile.loginInfo) flatMap {
          case Some(existingUser) => Future.successful(existingUser)
          case None => userRepository.create(profile.fullName.getOrElse("Unknown Name"))
        }
        // Create or get YouTube user
        ytUser <- ytUserRepository.getByChannelId(profile.loginInfo.providerKey) flatMap {
          case Some(existingYtUser) => 
            // Update YouTube user's profile info if needed
            ytUserRepository.updateProfile(
              profile.loginInfo.providerKey,
              profile.fullName,
              profile.email,
              profile.avatarURL
            ).map(_ => existingYtUser)
          case None => 
            // Create new YouTube user
            val now = Instant.now()
            val newYtUser = YtUser(
              userChannelId = profile.loginInfo.providerKey,
              userId = user.userId,
              displayName = profile.fullName,
              email = profile.email,
              profileImageUrl = profile.avatarURL,
              activated = true,
              createdAt = now,
              updatedAt = now
            )
            ytUserRepository.createFull(newYtUser)
        }
        // Save login info for this user
        _ <- loginInfoRepository.add(user.userId, profile.loginInfo)
        // Save OAuth2 info for this user
        _ <- oauth2InfoRepository.save(profile.loginInfo, authInfo)
        authenticator <- silhouette.env.authenticatorService.create(profile.loginInfo)
        value <- silhouette.env.authenticatorService.init(authenticator)
        result <- silhouette.env.authenticatorService.embed(value, Redirect(routes.HomeController.index()))
      } yield {
        silhouette.env.eventBus.publish(LoginEvent(user, request))
        result
      }
    }.recover {
      case e: ProviderException =>
        BadRequest(s"Authentication error: ${e.getMessage}")
    }
  }

  /**
   * Logs out the user.
   * @return The result to display.
   */
  def signOut = silhouette.SecuredAction.async { implicit request =>
    val result = Redirect(routes.AuthController.login)
    silhouette.env.eventBus.publish(LogoutEvent(request.identity, request))
    silhouette.env.authenticatorService.discard(request.authenticator, result)
  }
  
  /**
   * Gets the current logged-in user info.
   * @return The result to display.
   */
  def userInfo = silhouette.SecuredAction.async { implicit request =>
    val user = request.identity
    
    // Get YouTube accounts for this user
    ytUserRepository.getByUserId(user.userId).map { ytUsers =>
      Ok(Json.obj(
        "user" -> user,
        "youtubeAccounts" -> ytUsers
      ))
    }
  }
}
