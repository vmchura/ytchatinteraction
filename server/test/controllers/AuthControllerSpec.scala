package controllers

import models.repository.{LoginInfoRepository, OAuth2InfoRepository, UserRepository, YtStreamerRepository, YtUserRepository}
import models.{User, YtStreamer, YtUser}
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
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
import play.silhouette.api.actions.UnsecuredActionBuilder

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.time.Instant
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer

class AuthControllerSpec extends PlaySpec with MockitoSugar with ScalaFutures {
  
  implicit val actorSystem: ActorSystem = ActorSystem("test")
  implicit val materializer: Materializer = Materializer(actorSystem)

  "AuthController#authenticate" should {
    "redirect to provider authorization page" in {
      // Mocks
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUserService = mock[UserService]
      val mockAuthInfoRepository = mock[AuthInfoRepository]
      val mockSocialProviderRegistry = mock[SocialProviderRegistry]
      val mockYtUserRepository = mock[YtUserRepository]
      val mockLoginInfoRepository = mock[LoginInfoRepository]
      val mockOAuth2InfoRepository = mock[OAuth2InfoRepository]
      val mockYouTubeProvider = mock[YouTubeProvider]
      val ytStreamerRepository = mock[YtStreamerRepository]
      val mockYoutubeMembershipService = mock[services.YoutubeMembershipService]

      // Create a fake request
      val fakeRequest = FakeRequest()
      
      // Mock the YouTube provider to return a redirect result with the request
      val redirectResult = Results.Redirect("https://accounts.google.com/o/oauth2/auth")
      when(mockYouTubeProvider.authenticate()(any())).thenReturn(Future.successful(Left(redirectResult)))
      
      // Mock the social provider registry
      when(mockSocialProviderRegistry.get[YouTubeProvider]).thenReturn(Some(mockYouTubeProvider))
      when(ytStreamerRepository.getByChannelId(any())).thenReturn(Future.successful(None))

      // Mock the UnsecuredAction to execute the function directly  
      val mockUnsecuredAction = mock[UnsecuredActionBuilder[DefaultEnv, AnyContent]]
      when(mockSilhouette.UnsecuredAction).thenReturn(mockUnsecuredAction)
      
      // Create a real action that will execute the business logic
      import play.api.mvc.ActionBuilderImpl
      val realActionBuilder = new ActionBuilderImpl(stubControllerComponents().parsers.default)(global)
      when(mockUnsecuredAction.async(any[Request[AnyContent] => Future[Result]])).thenAnswer { invocation =>
        val func = invocation.getArgument[Request[AnyContent] => Future[Result]](0)
        realActionBuilder.async(func)
      }

      // Create controller
      val controller = new AuthController(
        stubControllerComponents(),
        mockSilhouette,
        mockUserService,
        mockAuthInfoRepository,
        mockSocialProviderRegistry,
        mockYtUserRepository,
        mockLoginInfoRepository,
        mockOAuth2InfoRepository,
        ytStreamerRepository,
        mockYoutubeMembershipService
      )
      
      // Execute the test - call the actual controller method
      val result = call(controller.authenticate("youtube"), fakeRequest)
      
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
      val mockYtUserRepository = mock[YtUserRepository]
      val mockLoginInfoRepository = mock[LoginInfoRepository]
      val mockOAuth2InfoRepository = mock[OAuth2InfoRepository]
      val mockYouTubeProvider = mock[YouTubeProvider]
      val mockYtStreamerRepository = mock[YtStreamerRepository]
      val mockYoutubeMembershipService = mock[services.YoutubeMembershipService]

      // Mock the social provider registry
      when(mockSocialProviderRegistry.get[YouTubeProvider]).thenReturn(Some(mockYouTubeProvider))
      when(mockYtStreamerRepository.getByChannelId(any())).thenReturn(Future.successful(None))
      
      // Create a fake request
      val fakeRequest = FakeRequest()
      
      // Mock the YouTube provider to throw an exception
      when(mockYouTubeProvider.authenticate()(any()))
        .thenReturn(Future.failed(new ProviderException("Authentication failed")))

      // Mock the UnsecuredAction to execute the function directly  
      val mockUnsecuredAction = mock[UnsecuredActionBuilder[DefaultEnv, AnyContent]]
      when(mockSilhouette.UnsecuredAction).thenReturn(mockUnsecuredAction)
      
      // Create a real action that will execute the business logic
      import play.api.mvc.ActionBuilderImpl
      val realActionBuilder = new ActionBuilderImpl(stubControllerComponents().parsers.default)(global)
      when(mockUnsecuredAction.async(any[Request[AnyContent] => Future[Result]])).thenAnswer { invocation =>
        val func = invocation.getArgument[Request[AnyContent] => Future[Result]](0)
        realActionBuilder.async(func)
      }

      // Create controller
      val controller = new AuthController(
        stubControllerComponents(),
        mockSilhouette,
        mockUserService,
        mockAuthInfoRepository,
        mockSocialProviderRegistry,
        mockYtUserRepository,
        mockLoginInfoRepository,
        mockOAuth2InfoRepository,
        mockYtStreamerRepository,
        mockYoutubeMembershipService
      )
      
      // Execute the test - call the actual controller method
      val result = call(controller.authenticate("youtube"), fakeRequest)
      
      // Verify the error response
      status(result) mustEqual BAD_REQUEST
      contentAsString(result) must include("Authentication error")
      
      // Verify that the provider was called
      verify(mockYouTubeProvider).authenticate()(any())
    }
  }
}
