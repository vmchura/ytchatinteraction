package modules

import com.google.inject.{AbstractModule, Provides}
import com.google.inject.name.Named
import controllers.{DefaultRememberMeConfig, DefaultSilhouetteControllerComponents, RememberMeConfig, SilhouetteControllerComponents}
import models.repository.OAuth2InfoRepository
import net.codingwell.scalaguice.ScalaModule
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc.{Cookie, CookieHeaderEncoding}
import play.silhouette.api.{Environment, EventBus, Silhouette, SilhouetteProvider}
import play.silhouette.api.crypto.{Crypter, CrypterAuthenticatorEncoder, Signer}
import play.silhouette.api.repositories.AuthInfoRepository
import play.silhouette.api.services.{AuthenticatorService, AvatarService}
import play.silhouette.api.util.{Clock, FingerprintGenerator, HTTPLayer, IDGenerator, PasswordHasherRegistry, PlayHTTPLayer}
import play.silhouette.crypto.{JcaCrypter, JcaCrypterSettings, JcaSigner, JcaSignerSettings}
import play.silhouette.impl.authenticators.{CookieAuthenticator, CookieAuthenticatorService, CookieAuthenticatorSettings}
import play.silhouette.impl.providers.*
import play.silhouette.impl.providers.state.{CsrfStateItemHandler, CsrfStateSettings}
import play.silhouette.impl.services.GravatarService
import play.silhouette.impl.util.{DefaultFingerprintGenerator, SecureRandomIDGenerator}
import play.silhouette.password.{BCryptPasswordHasher, BCryptSha256PasswordHasher}
import play.silhouette.persistence.daos.DelegableAuthInfoDAO
import play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import providers.YouTubeProvider
import services.{UserService, UserServiceImpl}
import utils.auth.{CustomSecuredErrorHandler, CustomUnsecuredErrorHandler}
import play.silhouette.api.actions.{SecuredErrorHandler, UnsecuredErrorHandler}
import play.api.i18n.MessagesApi

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.reflect.ClassTag
import play.silhouette.impl.providers.oauth2.GoogleProvider

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
    bind[FingerprintGenerator].toInstance(new DefaultFingerprintGenerator(false))
    bind[Clock].toInstance(Clock())

    // Configure Auth Info Repository
    bind[DelegableAuthInfoDAO[OAuth2Info]].to[OAuth2InfoRepository]
    
    // Explicitly bind the ClassTag for OAuth2Info
    bind[ClassTag[OAuth2Info]].toInstance(implicitly[ClassTag[OAuth2Info]])
    
    // Error handlers are provided via @Provides methods below
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

  @Provides
  def provideAuthInfoRepository(
                                 oauth2InfoDAO: DelegableAuthInfoDAO[OAuth2Info]): AuthInfoRepository = {

    new DelegableAuthInfoRepository(oauth2InfoDAO)
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
      sameSite = configuration.getOptional[String]("silhouette.csrfStateItemHandler.sameSite").flatMap(Cookie.SameSite.parse),
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
                                 @Named("authenticator-signer") signer: Signer,
                                 csrfStateItemHandler: CsrfStateItemHandler): SocialStateHandler = {

    new DefaultSocialStateHandler(Set(csrfStateItemHandler), signer)
  }

  /**
   * Provides the YouTube provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param socialStateHandler The social state handler.
   * @param configuration The YouTube OAuth2 settings.
   * @return The YouTube provider.
   */
  @Provides
  def provideGoogleProvider(
    httpLayer: HTTPLayer,
    socialStateHandler: SocialStateHandler,
    configuration: Configuration): GoogleProvider = {
    val config = configuration.get[Configuration]("silhouette.google")
    val settings = OAuth2Settings(
      authorizationURL = Some(config.get[String]("authorizationURL")),
      accessTokenURL = config.get[String]("accessTokenURL"),
      redirectURL = Some(config.get[String]("redirectURL")),
      clientID = config.get[String]("clientID"),
      clientSecret = config.get[String]("clientSecret"),
      scope = Some(config.get[String]("scope")),
      authorizationParams = Map("access_type" -> "offline"))
    new GoogleProvider(httpLayer, socialStateHandler, settings)
  }
  
  /**
   * Provides the social provider registry.
   *
   * @param youTubeProvider The YouTube provider implementation.
   * @return The social provider registry.
   */
  @Provides
  def provideSocialProviderRegistry(
    googleProvider: GoogleProvider
  ): SocialProviderRegistry = {
    SocialProviderRegistry(Seq(googleProvider))
  }

  @Provides
  def providePasswordHasherRegistry(): PasswordHasherRegistry = {
    PasswordHasherRegistry(new BCryptSha256PasswordHasher(), Seq(new BCryptPasswordHasher()))
  }

  @Provides
  def provideCredentialsProvider(
                                  authInfoRepository: AuthInfoRepository,
                                  passwordHasherRegistry: PasswordHasherRegistry): CredentialsProvider = {

    new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
  }

  @Provides
  def providesRememberMeConfig(configuration: Configuration): RememberMeConfig = {

    DefaultRememberMeConfig(
      expiry = FiniteDuration.apply(configuration.underlying.getDuration("silhouette.authenticator.rememberMe.authenticatorExpiry").toMinutes, TimeUnit.MINUTES),
      idleTimeout = Some(FiniteDuration.apply(configuration.underlying.getDuration("silhouette.authenticator.rememberMe.authenticatorIdleTimeout").toMinutes, TimeUnit.MINUTES)),
      cookieMaxAge = Some(FiniteDuration.apply(configuration.underlying.getDuration("silhouette.authenticator.rememberMe.cookieMaxAge").toMinutes, TimeUnit.MINUTES))
    )
  }

  @Provides
  def provideAvatarService(httpLayer: HTTPLayer): AvatarService = new GravatarService(httpLayer)

  @Provides
  def providesSilhouetteComponents(components: DefaultSilhouetteControllerComponents): SilhouetteControllerComponents = {
    components
  }
  
  /**
   * Provides the custom secured error handler.
   *
   * @param messagesApi The messages API.
   * @return The secured error handler.
   */
  @Provides
  def provideSecuredErrorHandler(messagesApi: MessagesApi): SecuredErrorHandler = {
    new CustomSecuredErrorHandler(messagesApi)
  }

  /**
   * Provides the custom unsecured error handler.
   *
   * @param messagesApi The messages API.
   * @return The unsecured error handler.
   */
  @Provides  
  def provideUnsecuredErrorHandler(messagesApi: MessagesApi): UnsecuredErrorHandler = {
    new CustomUnsecuredErrorHandler(messagesApi)
  }
}
