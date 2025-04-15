package controllers

import models.repository.{LoginInfoRepository, OAuth2InfoRepository, UserRepository, YtUserRepository}
import models.{User, YtUser}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._
import play.silhouette.api.repositories.AuthInfoRepository
import play.silhouette.api.{Environment, EventBus, LoginInfo, Silhouette}
import play.silhouette.impl.authenticators.CookieAuthenticatorService
import play.silhouette.impl.providers.{CommonSocialProfile, OAuth2Info, SocialProviderRegistry}
import providers.YouTubeProvider
import services.UserService
import modules.DefaultEnv
import play.silhouette.api.exceptions.ProviderException

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.time.Instant

class AuthControllerSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  "AuthController#authenticate" should {
    "redirect to provider authorization page" in {
      // Mocks
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUserService = mock[UserService]
      val mockAuthInfoRepository = mock[AuthInfoRepository]
      val mockSocialProviderRegistry = mock[SocialProviderRegistry]
      val mockUserRepository = mock[UserRepository]
      val mockYtUserRepository = mock[YtUserRepository]
      val mockLoginInfoRepository = mock[LoginInfoRepository]
      val mockOAuth2InfoRepository = mock[OAuth2InfoRepository]
      val mockYouTubeProvider = mock[YouTubeProvider]

      // Mock the environment
      val mockEnvironment = mock[Environment[DefaultEnv]]
      when(mockSilhouette.env).thenReturn(mockEnvironment)

      // Mock the social provider registry
      when(mockSocialProviderRegistry.get[YouTubeProvider]).thenReturn(Some(mockYouTubeProvider))
      
      // Create a fake request
      val fakeRequest = FakeRequest()
      
      // Mock the YouTube provider to return a redirect result with the request
      val redirectResult = Results.Redirect("https://accounts.google.com/o/oauth2/auth")
      when(mockYouTubeProvider.authenticate()(any())).thenReturn(Future.successful(Left(redirectResult)))
      
      // Create controller
      val controller = new AuthController(
        stubControllerComponents(),
        mockSilhouette,
        mockUserService,
        mockAuthInfoRepository,
        mockSocialProviderRegistry,
        mockUserRepository,
        mockYtUserRepository,
        mockLoginInfoRepository,
        mockOAuth2InfoRepository
      )
      
      // Execute the test
      val result = controller.authenticate("youtube")(fakeRequest)
      
      // Verify the redirect
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("https://accounts.google.com/o/oauth2/auth")
      
      // Verify that the provider was called
      verify(mockYouTubeProvider).authenticate()(any())
    }
    
    "handle authentication error gracefully" in {
      // Mocks
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUserService = mock[UserService]
      val mockAuthInfoRepository = mock[AuthInfoRepository]
      val mockSocialProviderRegistry = mock[SocialProviderRegistry]
      val mockUserRepository = mock[UserRepository]
      val mockYtUserRepository = mock[YtUserRepository]
      val mockLoginInfoRepository = mock[LoginInfoRepository]
      val mockOAuth2InfoRepository = mock[OAuth2InfoRepository]
      val mockYouTubeProvider = mock[YouTubeProvider]

      // Mock the environment
      val mockEnvironment = mock[Environment[DefaultEnv]]
      when(mockSilhouette.env).thenReturn(mockEnvironment)

      // Mock the social provider registry
      when(mockSocialProviderRegistry.get[YouTubeProvider]).thenReturn(Some(mockYouTubeProvider))
      
      // Create a fake request
      val fakeRequest = FakeRequest()
      
      // Mock the YouTube provider to throw an exception
      when(mockYouTubeProvider.authenticate()(any()))
        .thenReturn(Future.failed(new ProviderException("Authentication failed")))
      
      // Create controller
      val controller = new AuthController(
        stubControllerComponents(),
        mockSilhouette,
        mockUserService,
        mockAuthInfoRepository,
        mockSocialProviderRegistry,
        mockUserRepository,
        mockYtUserRepository,
        mockLoginInfoRepository,
        mockOAuth2InfoRepository
      )
      
      // Execute the test
      val result = controller.authenticate("youtube")(fakeRequest)
      
      // Verify the error response
      status(result) mustEqual BAD_REQUEST
      contentAsString(result) must include("Authentication error")
      
      // Verify that the provider was called
      verify(mockYouTubeProvider).authenticate()(any())
    }
    
    "handle successful authentication with creation of new user" in {
      // Test data
      val userId = 1L
      val userName = "Test User"
      val user = User(userId, userName)
      val channelId = "channel123"
      val email = "test@example.com"
      val profileImageUrl = "http://example.com/avatar.jpg"
      val loginInfo = LoginInfo("youtube", channelId)
      val oauth2Info = OAuth2Info(
        accessToken = "access-token",
        tokenType = Some("bearer"),
        expiresIn = Some(3600),
        refreshToken = Some("refresh-token")
      )
      val profile = CommonSocialProfile(
        loginInfo = loginInfo,
        firstName = None,
        lastName = None,
        fullName = Some(userName),
        email = Some(email),
        avatarURL = Some(profileImageUrl)
      )
      val ytUser = YtUser(
        userChannelId = channelId,
        userId = userId,
        displayName = Some(userName),
        email = Some(email),
        profileImageUrl = Some(profileImageUrl),
        activated = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
      )
      
      // Mocks
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUserService = mock[UserService]
      val mockAuthInfoRepository = mock[AuthInfoRepository]
      val mockSocialProviderRegistry = mock[SocialProviderRegistry]
      val mockUserRepository = mock[UserRepository]
      val mockYtUserRepository = mock[YtUserRepository]
      val mockLoginInfoRepository = mock[LoginInfoRepository]
      val mockOAuth2InfoRepository = mock[OAuth2InfoRepository]
      val mockYouTubeProvider = mock[YouTubeProvider]
      val mockAuthenticatorService = mock[CookieAuthenticatorService]
      val mockEventBus = mock[EventBus]

      // Mock the environment
      val mockEnvironment = mock[Environment[DefaultEnv]]
      when(mockSilhouette.env).thenReturn(mockEnvironment)
      when(mockEnvironment.authenticatorService).thenReturn(mockAuthenticatorService)
      when(mockEnvironment.eventBus).thenReturn(mockEventBus)

      // Mock the social provider registry
      when(mockSocialProviderRegistry.get[YouTubeProvider]).thenReturn(Some(mockYouTubeProvider))
      
      // Create a fake request
      val fakeRequest = FakeRequest()
      
      // Mock the YouTube provider to return OAuth2Info
      when(mockYouTubeProvider.authenticate()(any()))
        .thenReturn(Future.successful(Right(oauth2Info)))
      
      // Mock profile retrieval
      when(mockYouTubeProvider.retrieveProfile(any[OAuth2Info]()))
        .thenReturn(Future.successful(profile))
      
      // Mock login info repository to find no existing user
      when(mockLoginInfoRepository.findUser(any[LoginInfo]()))
        .thenReturn(Future.successful(None))
      
      // Mock user repository to create a new user
      when(mockUserRepository.create(any[String]()))
        .thenReturn(Future.successful(user))
      
      // Mock YouTube user repository to indicate no existing YouTube user
      when(mockYtUserRepository.getByChannelId(channelId))
        .thenReturn(Future.successful(None))
      
      // Mock YouTube user repository to create a new YouTube user
      when(mockYtUserRepository.createFull(any[YtUser]()))
        .thenReturn(Future.successful(ytUser))
      
      // Mock login info repository to add login info
      when(mockLoginInfoRepository.add(any[Long](), any[LoginInfo]()))
        .thenReturn(Future.successful(models.LoginInfo(Some(1L), "youtube", channelId, userId)))
      
      // Mock OAuth2 info repository
      when(mockOAuth2InfoRepository.save(any[LoginInfo](), any[OAuth2Info]()))
        .thenReturn(Future.successful(oauth2Info))
      
      // Mock authenticator service
      val cookieAuth = mock[play.silhouette.impl.authenticators.CookieAuthenticator]
      
      // Create a mock cookie for the authenticator
      val authCookie = mock[play.api.mvc.Cookie]
      
      when(mockAuthenticatorService.create(any[LoginInfo]())(any()))
        .thenReturn(Future.successful(cookieAuth))
      when(mockAuthenticatorService.init(any())(any()))
        .thenReturn(Future.successful(authCookie))
      
      // Mock the embed method with cookie and result
      val redirectResult = Results.Redirect(routes.HomeController.index())
      when(mockAuthenticatorService.embed(any[play.api.mvc.Cookie](), any[Result]())(any()))
        .thenReturn(Future.successful(redirectResult))
      
      // Create controller
      val controller = new AuthController(
        stubControllerComponents(),
        mockSilhouette,
        mockUserService,
        mockAuthInfoRepository,
        mockSocialProviderRegistry,
        mockUserRepository,
        mockYtUserRepository,
        mockLoginInfoRepository,
        mockOAuth2InfoRepository
      )
      println("Hello 1")
      // Execute the test
      val result = controller.authenticate("youtube")(fakeRequest)
      println("Hello 2")
      // Verify the redirect to home page
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) must not be None
      println("Hello 3")

      // Verify repository interactions
      verify(mockUserRepository).create(profile.fullName.getOrElse("Unknown Name"))
      verify(mockYtUserRepository).createFull(any[YtUser]())
      verify(mockLoginInfoRepository).add(userId, profile.loginInfo)
      verify(mockOAuth2InfoRepository).save(profile.loginInfo, oauth2Info)
      verify(mockEventBus).publish(any())
    }
  }
}
