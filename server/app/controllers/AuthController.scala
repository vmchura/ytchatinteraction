package controllers

import models.repository.{LoginInfoRepository, OAuth2InfoRepository, UserRepository, YtStreamerRepository, YtUserRepository}

import javax.inject.Inject
import models.{User, YtUser}
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, Cookie, Request, Result}
import play.silhouette.api.Authenticator.Implicits.*
import play.silhouette.api.exceptions.ProviderException
import play.silhouette.api.repositories.AuthInfoRepository
import play.silhouette.api.{LoginEvent, LogoutEvent, Silhouette}
import play.silhouette.impl.providers.{CommonSocialProfileBuilder, SocialProviderRegistry}
import providers.YouTubeProvider
import play.silhouette.impl.providers.CommonSocialProfile
import services.UserService

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import play.api.libs.json.Json
import play.silhouette.api.util.Credentials
import play.silhouette.api.LoginInfo
import modules.DefaultEnv
import java.util.UUID

/**
 * The authentication controller.
 */
class AuthController @Inject()(
  components: ControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  userService: UserService,
  authInfoRepository: AuthInfoRepository,
  socialProviderRegistry: SocialProviderRegistry,
  ytUserRepository: YtUserRepository,
  loginInfoRepository: LoginInfoRepository,
  oauth2InfoRepository: OAuth2InfoRepository,
  ytStreamerRepository: YtStreamerRepository
)(implicit ex: ExecutionContext) extends AbstractController(components) with I18nSupport {

  /**
   * Displays the login page with YouTube authentication button.
   * Only accessible to non-authenticated users.
   *
   * @return The result to display.
   */
  def login: Action[AnyContent] = silhouette.UnsecuredAction { implicit request =>
    Ok(views.html.login())
  }

  /**
   * Handles the YouTube authentication.
   * Only accessible to non-authenticated users.
   *
   * @return The result to display.
   */
  def authenticate(provider: String) = silhouette.UnsecuredAction.async { implicit request =>
    (socialProviderRegistry.get[YouTubeProvider] match {
      case Some(p) => p.authenticate()
      case None => Future.failed(new ProviderException(s"Cannot authenticate with unknown provider $provider"))
    }).flatMap {
      case Left(result) => Future.successful(result)
      case Right(authInfo) => for {
        profile <- socialProviderRegistry.get[YouTubeProvider].get.retrieveProfile(authInfo)
        user <- loginInfoRepository.findUser(profile.loginInfo) flatMap {
          case Some(existingUser) => Future.successful(existingUser)
          case None => userService.createUserWithAlias()
        }

        // Check if the channel exists as a YtStreamer
        existingYtStreamer <- ytStreamerRepository.getByChannelId(profile.loginInfo.providerKey)

        // Handle YtStreamer creation or owner assignment based on cases
        _ <- existingYtStreamer match {
          case None =>
            // Case 1: New user login and channel doesn't exist - create YtStreamer and assign ownership
            ytStreamerRepository.create(profile.loginInfo.providerKey, Some(user.userId), channelTitle=profile.fullName)

          case Some(streamer) if streamer.ownerUserId.isEmpty =>
            // Case 2: Channel exists but has no owner - assign ownership to this user
            ytStreamerRepository.updateOwner(profile.loginInfo.providerKey, Some(user.userId))

          case Some(_) =>
            // Case 2 (alternate): Channel exists and already has an owner - do nothing
            Future.successful(())
        }

        // Continue with YtUser creation/update as before
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

        loginInfoInsert <- loginInfoRepository.add(user.userId, profile.loginInfo)
        oauthInfoInsert <- oauth2InfoRepository.save(profile.loginInfo, authInfo)
        authenticator <- silhouette.env.authenticatorService.create(profile.loginInfo)
        value <- silhouette.env.authenticatorService.init(authenticator)
        result <- silhouette.env.authenticatorService.embed(value, Redirect(routes.HomeController.index()).withSession("userId" -> user.userId.toString()))
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
      .flashing("success" -> "You have been logged out successfully.")
      .withNewSession
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
