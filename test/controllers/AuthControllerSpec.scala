package controllers

import models.repository.{LoginInfoRepository, OAuth2InfoRepository, UserRepository, YtUserRepository}
import models.{User, YtUser}
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.*
import play.api.test.*
import play.api.test.Helpers.*
import play.silhouette.api.repositories.AuthInfoRepository
import play.silhouette.api.{Environment, EventBus, LoginEvent, LoginInfo, Silhouette, SilhouetteEvent}
import play.silhouette.impl.authenticators.{CookieAuthenticator, CookieAuthenticatorService}
import play.silhouette.impl.providers.{CommonSocialProfile, OAuth2Info, SocialProviderRegistry}
import providers.YouTubeProvider
import services.UserService
import modules.DefaultEnv
import play.silhouette.api.exceptions.ProviderException
import play.silhouette.api.services.AuthenticatorResult

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

    "successfully register a login when authentication succeeds" in {
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
      val mockEnvironment = mock[Environment[DefaultEnv]]
      val mockAuthenticatorService = mock[CookieAuthenticatorService]
      class TestEventBus extends EventBus {
        var timesCalled: Int = 0
        override def publish(event: SilhouetteEvent): Unit = {
          timesCalled += 1
        }
      }

      // Use this in your test
      val testEventBus = new TestEventBus()

      // Create a fake request
      val fakeRequest = FakeRequest()
      
      // Set up the authentication flow
      when(mockSilhouette.env).thenReturn(mockEnvironment)
      when(mockEnvironment.authenticatorService).thenReturn(mockAuthenticatorService)
      when(mockEnvironment.eventBus).thenReturn(testEventBus)
      when(mockSocialProviderRegistry.get[YouTubeProvider]).thenReturn(Some(mockYouTubeProvider))
      
      // Mock OAuth2 authentication info
      val mockOAuth2Info = OAuth2Info(accessToken = "test-access-token")
      when(mockYouTubeProvider.authenticate()(any())).thenReturn(Future.successful(Right(mockOAuth2Info)))
      
      // Mock profile retrieval
      val loginInfo = LoginInfo(providerID = "youtube", providerKey = "channel123")
      val profile = CommonSocialProfile(
        loginInfo = loginInfo,
        firstName = None,
        lastName = None,
        fullName = Some("Test User"),
        email = Some("test@example.com"),
        avatarURL = Some("https://example.com/avatar.jpg")
      )
      when(mockYouTubeProvider.retrieveProfile(any())).thenReturn(Future.successful(profile))
      
      // Mock user retrieval/creation
      val user = User(userId = 1L, userName = "Test User")
      when(mockLoginInfoRepository.findUser(loginInfo)).thenReturn(Future.successful(None)) // No existing user
      when(mockUserRepository.create(any())).thenReturn(Future.successful(user))
      //when(mockEventBus.publish(any())).thenReturn((): Unit)
      // Mock YouTube user creation
      val ytUser = YtUser(
        userChannelId = "channel123",
        userId = 1L,
        displayName = Some("Test User"),
        email = Some("test@example.com"),
        profileImageUrl = Some("https://example.com/avatar.jpg"),
        activated = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
      )

      when(mockYtUserRepository.getByChannelId(any())).thenReturn(Future.successful(None)) // No existing YtUser
      when(mockYtUserRepository.createFull(any())).thenReturn(Future.successful(ytUser))
      
      // Mock login info and OAuth2 storage
      when(mockLoginInfoRepository.add(any(), any())).thenReturn(Future.successful(models.LoginInfo(Some(1),"youtube", "channel123", 1L)))
      when(mockOAuth2InfoRepository.save(any(), any())).thenReturn(Future.successful(mockOAuth2Info))
      
      // Mock authenticator creation
      val mockAuthenticator = mock[CookieAuthenticator]
      when(mockAuthenticatorService.create(any())(any())).thenReturn(Future.successful(mockAuthenticator))
      // We need to pass the implicit request to init
      when(mockAuthenticatorService.init(any())(any())).thenReturn(Future.successful(play.api.mvc.Cookie("auth-cookie-value", "value")))
      
      // Mock authenticator embedding
      val successRedirect = AuthenticatorResult(Results.Redirect("/"))
      when(mockAuthenticatorService.embed(any(), any())(any())).thenReturn(Future.successful(successRedirect))
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
      
      // Verify redirection to home page
      status(result) mustEqual SEE_OTHER

      // Verify all the required operations were performed
      verify(mockYouTubeProvider).authenticate()(any())
      verify(mockYouTubeProvider).retrieveProfile(any())
      verify(mockLoginInfoRepository).findUser(loginInfo)
      verify(mockUserRepository).create("Test User")
      verify(mockYtUserRepository).getByChannelId("channel123")
      verify(mockYtUserRepository).createFull(any())
      verify(mockLoginInfoRepository).add(user.userId, loginInfo)
      verify(mockOAuth2InfoRepository).save(loginInfo, mockOAuth2Info)
      verify(mockAuthenticatorService, times(1)).create(any())(any())
      verify(mockAuthenticatorService).init(any())(any())
      verify(mockAuthenticatorService).embed(any(), any())(any())
      
      // Verify the login event was published
      assertResult(1)(testEventBus.timesCalled)
    }
  }
}
