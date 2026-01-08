package controllers

import models.repository.{LoginInfoRepository, OAuth2InfoRepository, UserRepository, YtStreamerRepository, YtUserRepository}

import javax.inject.Inject
import models.{User, YtUser}
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, Cookie, Request, Result}
import play.silhouette.api.Authenticator.Implicits.*
import play.silhouette.api.exceptions.ProviderException
import play.silhouette.impl.exceptions.OAuth2StateException
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
import play.silhouette.impl.providers.oauth2.GoogleProvider

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
    (socialProviderRegistry.get[GoogleProvider] match {
      case Some(p) => p.authenticate()
      case None => Future.failed(new ProviderException(s"Cannot authenticate with unknown provider $provider"))
    }).flatMap {
      case Left(result) => Future.successful(result)
      case Right(authInfo) => for {
        profile <- socialProviderRegistry.get[GoogleProvider].get.retrieveProfile(authInfo)
        user <- loginInfoRepository.findUser(profile.loginInfo) flatMap {
          case Some(existingUser) => Future.successful(existingUser)
          case None => userService.createUserWithAlias()
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
      case e: OAuth2StateException =>
        Redirect(routes.AuthController.login)
          .flashing("error" -> "Authentication failed: En esta plataforma se utiliza 'cookies' para la autenticación de usuarios, tienes que habilitarlas en tu navegador.")
      case e: ProviderException =>
        Redirect(routes.AuthController.login)
          .flashing("error" -> s"Authentication error: ${e.getMessage}")
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


}
