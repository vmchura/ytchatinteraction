package controllers

import evolutioncomplete._
import evolutioncomplete.WinnerShared.Cancelled
import models._
import models.repository.{UploadedFileRepository, UserRepository}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc._
import play.api.mvc.ActionBuilderImpl
import play.api.test._
import play.api.test.Helpers._
import play.silhouette.api.{Environment, Silhouette}
import play.silhouette.api.actions.SecuredActionBuilder
import services._
import modules.DefaultEnv
import upickle.default._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.time.Instant
import java.util.UUID
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer

class FileUploadControllerSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  implicit val actorSystem: ActorSystem = ActorSystem("test")
  implicit val materializer: Materializer = Materializer(actorSystem)

  val testUser: User = User(1L, "testUser")
  val testMatchId: Long = 100L
  val testTournamentId: Long = 200L
  val testSessionUUID: UUID = UUID.randomUUID()

  def createController(
                        mockSilhouette: Silhouette[DefaultEnv],
                        mockParseReplayFileService: ParseReplayFileService,
                        mockUploadSessionService: TournamentUploadSessionService,
                        mockFileStorageService: FileStorageService,
                        mockUploadedFileRepository: UploadedFileRepository,
                        mockTournamentService: TournamentService,
                        mockUserRepository: UserRepository,
                        mockUserActivityService: UserActivityService
                      ): FileUploadController = {
    val mockComponents = mock[DefaultSilhouetteControllerComponents]
    when(mockComponents.silhouette).thenReturn(mockSilhouette)
    when(mockComponents.messagesApi).thenReturn(stubMessagesApi())
    when(mockComponents.langs).thenReturn(stubLangs())
    when(mockComponents.fileMimeTypes).thenReturn(stubControllerComponents().fileMimeTypes)
    when(mockComponents.executionContext).thenReturn(global)
    when(mockComponents.actionBuilder).thenReturn(stubControllerComponents().actionBuilder)
    when(mockComponents.parsers).thenReturn(stubControllerComponents().parsers)

    new FileUploadController(
      mockComponents,
      mockParseReplayFileService,
      mockUploadSessionService,
      mockFileStorageService,
      mockUploadedFileRepository,
      mockTournamentService,
      mockUserRepository,
      mockUserActivityService
    )
  }

  def createMockSecuredAction(user: User): SecuredActionBuilder[DefaultEnv, AnyContent] = {
    val mockSecuredAction = mock[SecuredActionBuilder[DefaultEnv, AnyContent]]
    val realActionBuilder = new ActionBuilderImpl(stubControllerComponents().parsers.default)(global)

    when(mockSecuredAction.async(any[play.silhouette.api.actions.SecuredRequest[DefaultEnv, AnyContent] => Future[Result]])).thenAnswer { invocation =>
      val func = invocation.getArgument[play.silhouette.api.actions.SecuredRequest[DefaultEnv, AnyContent] => Future[Result]](0)
      realActionBuilder.async { request =>
        val securedRequest = mock[play.silhouette.api.actions.SecuredRequest[DefaultEnv, AnyContent]]
        when(securedRequest.identity).thenReturn(user)
        when(securedRequest.session).thenReturn(request.session)
        func(securedRequest)
      }
    }
    mockSecuredAction
  }

  def createTestSession(): TournamentSession = {
    TournamentSession(
      userId = testUser.userId,
      matchId = testMatchId,
      tournamentId = testTournamentId,
      uploadState = TournamentUploadStateShared(
        challongeMatchID = testMatchId,
        tournamentID = testTournamentId,
        firstParticipant = ParticipantShared(1L, "Player1", Set.empty),
        secondParticipant = ParticipantShared(2L, "Player2", Set.empty),
        games = Nil,
        winner = Cancelled
      ),
      hash2StoreInformation = Map.empty,
      lastUpdated = Instant.now()
    )
  }

  "FileUploadController#fetchState" should {
    "return Ok with upload state when session exists" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUploadSessionService = mock[TournamentUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockUploadedFileRepository = mock[UploadedFileRepository]
      val mockTournamentService = mock[TournamentService]
      val mockUserRepository = mock[UserRepository]
      val mockUserActivityService = mock[UserActivityService]

      val testSession = createTestSession()
      when(mockUploadSessionService.getOrCreateSession(any[MetaTournamentSession]))
        .thenReturn(Future.successful(Some(testSession)))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockParseReplayFileService,
        mockUploadSessionService,
        mockFileStorageService,
        mockUploadedFileRepository,
        mockTournamentService,
        mockUserRepository,
        mockUserActivityService
      )

      val result = call(controller.fetchState(testMatchId, testTournamentId), FakeRequest())

      status(result) mustEqual OK
      verify(mockUploadSessionService).getOrCreateSession(any[MetaTournamentSession])
    }

    "return BadRequest when session is not available" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUploadSessionService = mock[TournamentUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockUploadedFileRepository = mock[UploadedFileRepository]
      val mockTournamentService = mock[TournamentService]
      val mockUserRepository = mock[UserRepository]
      val mockUserActivityService = mock[UserActivityService]

      when(mockUploadSessionService.getOrCreateSession(any[MetaTournamentSession]))
        .thenReturn(Future.successful(None))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockParseReplayFileService,
        mockUploadSessionService,
        mockFileStorageService,
        mockUploadedFileRepository,
        mockTournamentService,
        mockUserRepository,
        mockUserActivityService
      )

      val result = call(controller.fetchState(testMatchId, testTournamentId), FakeRequest())

      status(result) mustEqual BAD_REQUEST
      contentAsString(result) must include("Session not available")
    }
  }

  "FileUploadController#removeFile" should {
    "return Ok with updated state when file is removed" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUploadSessionService = mock[TournamentUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockUploadedFileRepository = mock[UploadedFileRepository]
      val mockTournamentService = mock[TournamentService]
      val mockUserRepository = mock[UserRepository]
      val mockUserActivityService = mock[UserActivityService]

      val testSession = createTestSession()
      when(mockUploadSessionService.getOrCreateSession(any[MetaTournamentSession]))
        .thenReturn(Future.successful(Some(testSession)))
      when(mockUploadSessionService.removeFileFromSession(any[TournamentSession], any[UUID]))
        .thenReturn(testSession)
      when(mockUploadSessionService.persistState(any[TournamentSession]))
        .thenReturn(testSession)

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockParseReplayFileService,
        mockUploadSessionService,
        mockFileStorageService,
        mockUploadedFileRepository,
        mockTournamentService,
        mockUserRepository,
        mockUserActivityService
      )

      val result = call(controller.removeFile(testTournamentId, testMatchId, testSessionUUID), FakeRequest())

      status(result) mustEqual OK
      verify(mockUploadSessionService).removeFileFromSession(any[TournamentSession], any[UUID])
    }

    "return BadRequest when session is not available for removal" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUploadSessionService = mock[TournamentUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockUploadedFileRepository = mock[UploadedFileRepository]
      val mockTournamentService = mock[TournamentService]
      val mockUserRepository = mock[UserRepository]
      val mockUserActivityService = mock[UserActivityService]

      when(mockUploadSessionService.getOrCreateSession(any[MetaTournamentSession]))
        .thenReturn(Future.successful(None))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockParseReplayFileService,
        mockUploadSessionService,
        mockFileStorageService,
        mockUploadedFileRepository,
        mockTournamentService,
        mockUserRepository,
        mockUserActivityService
      )

      val result = call(controller.removeFile(testTournamentId, testMatchId, testSessionUUID), FakeRequest())

      status(result) mustEqual BAD_REQUEST
      contentAsString(result) must include("Session not available")
    }
  }
}