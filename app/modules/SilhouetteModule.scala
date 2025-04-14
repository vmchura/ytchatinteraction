package modules

import com.google.inject.{AbstractModule, Provides}
import com.google.inject.name.Named
import models.{LoginInfoRepository, OAuth2InfoRepository, User, UserRepository}
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc.{Cookie, CookieHeaderEncoding}
import play.silhouette.api.{Environment, EventBus, Silhouette, SilhouetteProvider}
import play.silhouette.api.crypto.{Crypter, CrypterAuthenticatorEncoder, Signer}
import play.silhouette.api.repositories.AuthInfoRepository
import play.silhouette.api.services.{AuthenticatorService, IdentityService}
import play.silhouette.api.util.{Clock, FingerprintGenerator, HTTPLayer, IDGenerator, PasswordHasherRegistry, PlayHTTPLayer}
import play.silhouette.crypto.{JcaCrypter, JcaCrypterSettings, JcaSigner, JcaSignerSettings}
import play.silhouette.impl.authenticators.{CookieAuthenticator, CookieAuthenticatorService, CookieAuthenticatorSettings}
import play.silhouette.impl.providers.{DefaultSocialStateHandler, SocialProviderRegistry, SocialStateHandler, OAuth2Info as SilhouetteOAuth2Info, OAuth2InfoService as SilhouetteOAuth2InfoService}
import play.silhouette.impl.providers.state.{CsrfStateItemHandler, CsrfStateSettings}
import play.silhouette.impl.util.{DefaultFingerprintGenerator, SecureRandomIDGenerator}
import play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import providers.{YouTubeProvider, YouTubeProviderFactory}
import services.{UserService, UserServiceImpl}

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.duration.*

/**
 * The Silhouette module.
 */
class SilhouetteModule extends AbstractModule with ScalaModule {

  /**
   * Configures the module.
   */
  override def configure(): Unit = {
    bind[Silhouette[DefaultEnv]].to[SilhouetteProvider[DefaultEnv]]
    bind[UserService].to[UserServiceImpl]
    bind[IDGenerator].toInstance(new SecureRandomIDGenerator())
    bind[Clock].toInstance(Clock())

    // Configure Auth Info Repository
    bind[AuthInfoRepository].to[DelegableAuthInfoRepository]
  }

  /**
   * Provides the HTTP layer implementation.
   *
   * @param client The Play WS client.
   * @return The HTTP layer implementation.
   */
  @Provides
  def provideHTTPLayer(client: WSClient): HTTPLayer = new PlayHTTPLayer(client)

  /**
   * Provides the Silhouette environment.
   *
   * @param userService          The user service implementation.
   * @param authenticatorService The authentication service implementation.
   * @param eventBus             The event bus instance.
   * @return The Silhouette environment.
   */
  @Provides
  def provideEnvironment(
    userService: UserService,
    authenticatorService: AuthenticatorService[CookieAuthenticator],
    eventBus: EventBus
  ): Environment[DefaultEnv] = {
    Environment[DefaultEnv](
      userService,
      authenticatorService,
      Seq(),
      eventBus
    )
  }

  /**
   * Provides the signer for the authenticator.
   *
   * @param configuration The Play configuration.
   * @return The signer for the authenticator.
   */
  @Provides
  @Named("authenticator-signer")
  def provideAuthenticatorSigner(configuration: Configuration): Signer = {
    val config = configuration.get[String]("silhouette.authenticator.signer.key")
    new JcaSigner(JcaSignerSettings(config))
  }

  /**
   * Provides the crypter for the authenticator.
   *
   * @param configuration The Play configuration.
   * @return The crypter for the authenticator.
   */
  @Provides
  @Named("authenticator-crypter")
  def provideAuthenticatorCrypter(configuration: Configuration): Crypter = {
    val config = configuration.get[String]("silhouette.authenticator.crypter.key")
    new JcaCrypter(JcaCrypterSettings(config))
  }

  /**
   * Provides the authenticator service.
   *
   * @param signer The signer implementation.
   * @param crypter The crypter implementation.
   * @param cookieHeaderEncoding Logic for encoding and decoding `Cookie` and `Set-Cookie` headers.
   * @param fingerprintGenerator The fingerprint generator implementation.
   * @param idGenerator The ID generator implementation.
   * @param configuration The Play configuration.
   * @param clock The clock instance.
   * @return The authenticator service.
   */
  @Provides
  def provideAuthenticatorService(
                                   @Named("authenticator-signer") signer: Signer,
                                   @Named("authenticator-crypter") crypter: Crypter,
                                   cookieHeaderEncoding: CookieHeaderEncoding,
                                   fingerprintGenerator: FingerprintGenerator,
                                   idGenerator: IDGenerator,
                                   configuration: Configuration,
                                   clock: Clock): AuthenticatorService[CookieAuthenticator] = {
    val config = CookieAuthenticatorSettings(
      cookieName = configuration.underlying.getString("silhouette.authenticator.cookieName"),
      cookiePath = configuration.underlying.getString("silhouette.authenticator.cookiePath"),
      secureCookie = configuration.underlying.getBoolean("silhouette.authenticator.secureCookie"),
      httpOnlyCookie = configuration.underlying.getBoolean("silhouette.authenticator.httpOnlyCookie"),
      sameSite = Cookie.SameSite.parse(configuration.underlying.getString("silhouette.authenticator.sameSite")),
      useFingerprinting = configuration.underlying.getBoolean("silhouette.authenticator.useFingerprinting"),
      authenticatorIdleTimeout = Some(FiniteDuration.apply(configuration.underlying.getDuration("silhouette.authenticator.authenticatorIdleTimeout").toMinutes, TimeUnit.MINUTES)),
      authenticatorExpiry = FiniteDuration.apply(configuration.underlying.getDuration("silhouette.authenticator.authenticatorExpiry").toMinutes, TimeUnit.MINUTES)
    )
    val authenticatorEncoder = new CrypterAuthenticatorEncoder(crypter)
    new CookieAuthenticatorService(config, None, signer, cookieHeaderEncoding, authenticatorEncoder, fingerprintGenerator, idGenerator, clock)

  }
  /**
   * Provides the OAuth2 info service.
   *
   * @param oauth2InfoRepository The OAuth2 info repository implementation.
   * @return The OAuth2 info service.
   */
  @Provides
  def provideOAuth2InfoService(
    oauth2InfoRepository: OAuth2InfoRepository
  ): SilhouetteOAuth2InfoService = {
    new SilhouetteOAuth2InfoService(oauth2InfoRepository)
  }

  /**
   * Provides the auth info repository.
   *
   * @param oauth2InfoService The OAuth2 info service implementation.
   * @return The auth info repository instance.
   */
  @Provides
  def provideAuthInfoRepository(
    oauth2InfoService: SilhouetteOAuth2InfoService
  ): DelegableAuthInfoRepository = {
    new DelegableAuthInfoRepository(oauth2InfoService)
  }

  /**
   * Provides the CSRF state item handler.
   *
   * @param idGenerator The ID generator implementation.
   * @param signer The signer implementation.
   * @param configuration The Play configuration.
   * @return The CSRF state item handler implementation.
   */
  @Provides
  def provideCsrfStateItemHandler(
    idGenerator: IDGenerator,
    @Named("authenticator-signer") signer: Signer,
    configuration: Configuration
  ): CsrfStateItemHandler = {
    val settings = CsrfStateSettings(
      cookieName = configuration.get[String]("silhouette.csrfStateItemHandler.cookieName"),
      cookiePath = configuration.get[String]("silhouette.csrfStateItemHandler.cookiePath"),
      cookieDomain = configuration.getOptional[String]("silhouette.csrfStateItemHandler.cookieDomain"),
      secureCookie = configuration.get[Boolean]("silhouette.csrfStateItemHandler.secureCookie"),
      httpOnlyCookie = configuration.get[Boolean]("silhouette.csrfStateItemHandler.httpOnlyCookie"),
      expirationTime = configuration.get[FiniteDuration]("silhouette.csrfStateItemHandler.expirationTime")
    )

    new CsrfStateItemHandler(settings, idGenerator, signer)
  }

  /**
   * Provides the social state handler.
   *
   * @param csrfStateItemHandler The CSRF state item handler implementation.
   * @return The social state handler implementation.
   */
  @Provides
  def provideSocialStateHandler(
                                 @Named("social-state-signer") signer: Signer,
                                 csrfStateItemHandler: CsrfStateItemHandler): SocialStateHandler = {

    new DefaultSocialStateHandler(Set(csrfStateItemHandler), signer)
  }
  
  /**
   * Provides the YouTube provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateHandler The social state handler.
   * @param settings The YouTube OAuth2 settings.
   * @return The YouTube provider.
   */
  @Provides
  def provideYouTubeProvider(
    httpLayer: HTTPLayer,
    socialStateHandler: SocialStateHandler,
    configuration: Configuration): YouTubeProvider = {
    new YouTubeProvider(httpLayer, stateHandler, configuration.underlying.as[OAuth2Settings]("silhouette.youtube"))
  }
  
  /**
   * Provides the social provider registry.
   *
   * @param youTubeProvider The YouTube provider implementation.
   * @return The social provider registry.
   */
  @Provides
  def provideSocialProviderRegistry(
    youTubeProvider: YouTubeProvider
  ): SocialProviderRegistry = {
    SocialProviderRegistry(Seq(youTubeProvider))
  }
}
