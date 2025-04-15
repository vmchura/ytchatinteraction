package controllers

import models.repository.{LoginInfoRepository, OAuth2InfoRepository, UserRepository, YtUserRepository}
import models.{User, YtUser}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.mockito.stubbing.Answer
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
  }
}
