package controllers

import evolutioncomplete._
import evolutioncomplete.WinnerShared.Cancelled
import evolutioncomplete.GameStateShared.ValidGame
import forms.AnalyticalFileData
import models._
import models.StarCraftModels.{SCPlayer, Terran, Zerg}
import models.repository.{AnalyticalFileRepository, UserRepository}
import models.ServerStarCraftModels.ReplayParsed
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc._
import play.api.mvc.ActionBuilderImpl
import play.api.test._
import play.api.test.Helpers._
import play.api.test.CSRFTokenHelper._
import play.silhouette.api.Silhouette
import play.silhouette.api.actions.SecuredActionBuilder
import services._
import modules.DefaultEnv

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.time.Instant
import java.util.UUID
import java.nio.file.Paths
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer

class AnalyticalUploadControllerSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  implicit val actorSystem: ActorSystem = ActorSystem("test")
  implicit val materializer: Materializer = Materializer(actorSystem)

  val testUser: User = User(1L, "testUser")

  def createController(
                        mockSilhouette: Silhouette[DefaultEnv],
                        mockParseReplayFileService: ParseReplayFileService,
                        mockUploadSessionService: AnalyticalUploadSessionService,
                        mockFileStorageService: FileStorageService,
                        mockAnalyticalFileRepository: AnalyticalFileRepository,
                        mockTournamentService: TournamentService,
                        mockUserRepository: UserRepository,
                        mockAnalyticalReplayService: AnalyticalReplayService,
                        mockUserActivityService: UserActivityService
                      ): AnalyticalUploadController = {
    val mockComponents = mock[DefaultSilhouetteControllerComponents]
    when(mockComponents.silhouette).thenReturn(mockSilhouette)
    when(mockComponents.messagesApi).thenReturn(stubMessagesApi())
    when(mockComponents.langs).thenReturn(stubLangs())
    when(mockComponents.fileMimeTypes).thenReturn(stubControllerComponents().fileMimeTypes)
    when(mockComponents.executionContext).thenReturn(global)
    when(mockComponents.actionBuilder).thenReturn(stubControllerComponents().actionBuilder)
    when(mockComponents.parsers).thenReturn(stubControllerComponents().parsers)

    new AnalyticalUploadController(
      mockComponents,
      mockParseReplayFileService,
      mockUploadSessionService,
      mockFileStorageService,
      mockAnalyticalFileRepository,
      mockTournamentService,
      mockUserRepository,
      mockAnalyticalReplayService,
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
        when(securedRequest.cookies).thenReturn(request.cookies)
        when(securedRequest.method).thenReturn(request.method)
        when(securedRequest.body).thenReturn(request.body)
        when(securedRequest.headers).thenReturn(request.headers)
        when(securedRequest.attrs).thenReturn(request.attrs)
        when(securedRequest.connection).thenReturn(request.connection)
        when(securedRequest.flash).thenReturn(request.flash)
        when(securedRequest.target).thenReturn(request.target)
        when(securedRequest.uri).thenReturn(request.uri)
        when(securedRequest.path).thenReturn(request.path)
        when(securedRequest.version).thenReturn(request.version)
        when(securedRequest.queryString).thenReturn(request.queryString)
        when(securedRequest.acceptLanguages).thenReturn(Seq(play.api.i18n.Lang("en")))
        when(securedRequest.transientLang()).thenReturn(None)
        func(securedRequest)
      }
    }
    mockSecuredAction
  }

  def createTestAnalyticalSession(): AnalyticalSession = {
    val fileResult = FileProcessResult(
      fileName = "test.rep",
      originalSize = 1000L,
      contentType = "application/octet-stream",
      processedAt = Instant.now().toString,
      success = true,
      errorMessage = None,
      gameInfo = Some(ReplayParsed(
        Some("Fighting Spirit"),
        Some("2024-01-01"),
        models.StarCraftModels.OneVsOneMode,
        List(
          models.StarCraftModels.Team(1, List(SCPlayer("Player1", Terran, 1))),
          models.StarCraftModels.Team(2, List(SCPlayer("Player2", Zerg, 2)))
        ),
        1,
        Some(10000),
        play.api.libs.json.JsArray.empty
      )),
      sha256Hash = Some("abc123hash"),
      path = Paths.get("/tmp/test.rep")
    )

    AnalyticalSession(
      userId = testUser.userId,
      uploadState = AnalyticalUploadStateShared(
        firstParticipant = ParticipantShared(1L, "Player1", Set.empty),
        games = List(ValidGame(
          smurfs = Map("Player1" -> SCPlayer("Player1", Terran, 1), "Player2" -> SCPlayer("Player2", Zerg, 2)),
          mapName = "Fighting Spirit",
          playedAt = java.time.LocalDateTime.now(),
          hash = "abc123hash",
          sessionID = UUID.randomUUID(),
          frames = 10000
        ))
      ),
      storageInfo = Some(AnalyticalFileInfo(
        originalFileName = "test.rep",
        storedFileName = "stored_test.rep",
        storedPath = "/storage/stored_test.rep",
        size = 1000L,
        contentType = "application/octet-stream",
        storedAt = Instant.now(),
        userId = testUser.userId
      )),
      lastUpdated = Instant.now(),
      fileResult = fileResult
    )
  }

  "AnalyticalUploadController#uploadAnalyticalFile" should {
    "return Ok with upload page when user has files" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUploadSessionService = mock[AnalyticalUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockAnalyticalFileRepository = mock[AnalyticalFileRepository]
      val mockTournamentService = mock[TournamentService]
      val mockUserRepository = mock[UserRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserActivityService = mock[UserActivityService]

      when(mockAnalyticalFileRepository.findByUserId(testUser.userId))
        .thenReturn(Future.successful(Seq.empty))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockParseReplayFileService,
        mockUploadSessionService,
        mockFileStorageService,
        mockAnalyticalFileRepository,
        mockTournamentService,
        mockUserRepository,
        mockAnalyticalReplayService,
        mockUserActivityService
      )

      val request = CSRFTokenHelper.addCSRFToken(FakeRequest())
      val result = call(controller.uploadAnalyticalFile(), request)

      status(result) mustEqual OK
      verify(mockAnalyticalFileRepository).findByUserId(testUser.userId)
    }
  }

  "AnalyticalUploadController#finalizeSmurf" should {
    "redirect when session exists and hash matches" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUploadSessionService = mock[AnalyticalUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockAnalyticalFileRepository = mock[AnalyticalFileRepository]
      val mockTournamentService = mock[TournamentService]
      val mockUserRepository = mock[UserRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserActivityService = mock[UserActivityService]

      val testSession = createTestAnalyticalSession()
      when(mockUploadSessionService.getSession(f"${testUser.userId}"))
        .thenReturn(Some(testSession))
      when(mockAnalyticalFileRepository.create(any[AnalyticalFile]))
        .thenReturn(Future.successful(mock[AnalyticalFile]))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockParseReplayFileService,
        mockUploadSessionService,
        mockFileStorageService,
        mockAnalyticalFileRepository,
        mockTournamentService,
        mockUserRepository,
        mockAnalyticalReplayService,
        mockUserActivityService
      )

      val request = FakeRequest(POST, "/")
        .withFormUrlEncodedBody("playerID" -> "1", "fileHash" -> "abc123hash")

      val result = call(controller.finalizeSmurf(), request)

      status(result) mustEqual SEE_OTHER
    }

    "redirect when session does not exist" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUploadSessionService = mock[AnalyticalUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockAnalyticalFileRepository = mock[AnalyticalFileRepository]
      val mockTournamentService = mock[TournamentService]
      val mockUserRepository = mock[UserRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserActivityService = mock[UserActivityService]

      when(mockUploadSessionService.getSession(f"${testUser.userId}"))
        .thenReturn(None)

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockParseReplayFileService,
        mockUploadSessionService,
        mockFileStorageService,
        mockAnalyticalFileRepository,
        mockTournamentService,
        mockUserRepository,
        mockAnalyticalReplayService,
        mockUserActivityService
      )

      val request = FakeRequest(POST, "/")
        .withFormUrlEncodedBody("playerID" -> "1", "fileHash" -> "wronghash")

      val result = call(controller.finalizeSmurf(), request)

      status(result) mustEqual SEE_OTHER
    }

    "redirect when form has errors" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUploadSessionService = mock[AnalyticalUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockAnalyticalFileRepository = mock[AnalyticalFileRepository]
      val mockTournamentService = mock[TournamentService]
      val mockUserRepository = mock[UserRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserActivityService = mock[UserActivityService]

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockParseReplayFileService,
        mockUploadSessionService,
        mockFileStorageService,
        mockAnalyticalFileRepository,
        mockTournamentService,
        mockUserRepository,
        mockAnalyticalReplayService,
        mockUserActivityService
      )

      val request = FakeRequest(POST, "/")
        .withFormUrlEncodedBody("playerID" -> "invalid")

      val result = call(controller.finalizeSmurf(), request)

      status(result) mustEqual SEE_OTHER
    }
  }
}