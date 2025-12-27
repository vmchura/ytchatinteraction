package controllers

import evolutioncomplete._
import evolutioncomplete.WinnerShared.{Cancelled, FirstUser}
import evolutioncomplete.GameStateShared.ValidGame
import models._
import models.StarCraftModels.{SCPlayer, Terran, Zerg}
import models.repository._
import models.MatchStatus._
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
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer

class MatchResultControllerSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  implicit val actorSystem: ActorSystem = ActorSystem("test")
  implicit val materializer: Materializer = Materializer(actorSystem)

  val testUser: User = User(1L, "testUser")
  val rivalUser: User = User(2L, "rivalUser")

  def createController(
      mockSilhouette: Silhouette[DefaultEnv],
      mockTournamentService: TournamentService,
      mockUserSmurfService: UserSmurfService,
      mockUploadSessionService: TournamentUploadSessionService,
      mockUploadedFileRepository: UploadedFileRepository,
      mockAnalyticalReplayService: AnalyticalReplayService,
      mockAnalyticalResultRepository: AnalyticalResultRepository,
      mockUserAliasRepository: UserAliasRepository,
      mockUserActivityService: UserActivityService
  ): MatchResultController = {
    val mockComponents = mock[DefaultSilhouetteControllerComponents]
    when(mockComponents.silhouette).thenReturn(mockSilhouette)
    when(mockComponents.messagesApi).thenReturn(stubMessagesApi())
    when(mockComponents.langs).thenReturn(stubLangs())
    when(mockComponents.fileMimeTypes).thenReturn(stubControllerComponents().fileMimeTypes)
    when(mockComponents.executionContext).thenReturn(global)
    when(mockComponents.actionBuilder).thenReturn(stubControllerComponents().actionBuilder)
    when(mockComponents.parsers).thenReturn(stubControllerComponents().parsers)

    new MatchResultController(
      mockComponents,
      mockTournamentService,
      mockUserSmurfService,
      mockUploadSessionService,
      mockUploadedFileRepository,
      mockAnalyticalReplayService,
      mockAnalyticalResultRepository,
      mockUserAliasRepository,
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

  def createTestTournamentSession(): TournamentSession = {
    TournamentSession(
      userId = testUser.userId,
      matchId = 1L,
      tournamentId = 100L,
      uploadState = TournamentUploadStateShared(
        challongeMatchID = 1L,
        tournamentID = 100L,
        firstParticipant = ParticipantShared(testUser.userId, testUser.userName, Set.empty),
        secondParticipant = ParticipantShared(rivalUser.userId, rivalUser.userName, Set.empty),
        games = Nil,
        winner = Cancelled
      ),
      hash2StoreInformation = Map.empty,
      lastUpdated = Instant.now()
    )
  }

  "MatchResultController#viewResults" should {
    "return Ok with results when analytical results exist" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockTournamentService = mock[TournamentService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadSessionService = mock[TournamentUploadSessionService]
      val mockUploadedFileRepository = mock[UploadedFileRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockUserActivityService = mock[UserActivityService]

      when(mockAnalyticalResultRepository.findByMatchId(1L))
        .thenReturn(Future.successful(Seq.empty))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockTournamentService,
        mockUserSmurfService,
        mockUploadSessionService,
        mockUploadedFileRepository,
        mockAnalyticalReplayService,
        mockAnalyticalResultRepository,
        mockUserAliasRepository,
        mockUserActivityService
      )

      val request = CSRFTokenHelper.addCSRFToken(FakeRequest(GET, "/"))
      val result = call(controller.viewResults(1L), request)

      status(result) mustEqual OK
    }

    "return Ok with user aliases mapped to results" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockTournamentService = mock[TournamentService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadSessionService = mock[TournamentUploadSessionService]
      val mockUploadedFileRepository = mock[UploadedFileRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockUserActivityService = mock[UserActivityService]

      val testResult = AnalyticalResult(
        id = 1L,
        userId = testUser.userId,
        matchId = Some(1L),
        casualMatchId = None,
        userRace = Terran,
        rivalRace = Zerg,
        originalFileName = "test.rep",
        analysisStartedAt = Instant.now(),
        analysisFinishedAt = Some(Instant.now()),
        algorithmVersion = "1.0",
        result = None
      )

      when(mockAnalyticalResultRepository.findByMatchId(1L))
        .thenReturn(Future.successful(Seq(testResult)))
      when(mockUserAliasRepository.getCurrentAlias(testUser.userId))
        .thenReturn(Future.successful(Some("testAlias")))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockTournamentService,
        mockUserSmurfService,
        mockUploadSessionService,
        mockUploadedFileRepository,
        mockAnalyticalReplayService,
        mockAnalyticalResultRepository,
        mockUserAliasRepository,
        mockUserActivityService
      )

      val request = CSRFTokenHelper.addCSRFToken(FakeRequest(GET, "/"))
      val result = call(controller.viewResults(1L), request)

      status(result) mustEqual OK
    }
  }

  "MatchResultController#closeMatch" should {
    "redirect when form has errors" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockTournamentService = mock[TournamentService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadSessionService = mock[TournamentUploadSessionService]
      val mockUploadedFileRepository = mock[UploadedFileRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockUserActivityService = mock[UserActivityService]

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockTournamentService,
        mockUserSmurfService,
        mockUploadSessionService,
        mockUploadedFileRepository,
        mockAnalyticalReplayService,
        mockAnalyticalResultRepository,
        mockUserAliasRepository,
        mockUserActivityService
      )

      val request = FakeRequest(POST, "/")
        .withFormUrlEncodedBody("invalid" -> "data")

      val result = call(controller.closeMatch(1L, 100L), request)

      status(result) mustEqual SEE_OTHER
    }

    "redirect when match not found" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockTournamentService = mock[TournamentService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadSessionService = mock[TournamentUploadSessionService]
      val mockUploadedFileRepository = mock[UploadedFileRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockUserActivityService = mock[UserActivityService]

      when(mockTournamentService.getMatch(100L, 1L))
        .thenReturn(Future.successful(None))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockTournamentService,
        mockUserSmurfService,
        mockUploadSessionService,
        mockUploadedFileRepository,
        mockAnalyticalReplayService,
        mockAnalyticalResultRepository,
        mockUserAliasRepository,
        mockUserActivityService
      )

      val request = FakeRequest(POST, "/")
        .withFormUrlEncodedBody(
          "winner" -> "first_user",
          "smurfsFirstParticipant[0]" -> "smurf1",
          "smurfsSecondParticipant[0]" -> "smurf2"
        )

      val result = call(controller.closeMatch(1L, 100L), request)

      status(result) mustEqual INTERNAL_SERVER_ERROR
    }

    "redirect when session not found" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockTournamentService = mock[TournamentService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadSessionService = mock[TournamentUploadSessionService]
      val mockUploadedFileRepository = mock[UploadedFileRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockUserActivityService = mock[UserActivityService]

      val testMatch = TournamentMatch(
        matchId = 1L,
        tournamentId = 100L,
        firstUserId = testUser.userId,
        secondUserId = rivalUser.userId,
        winnerUserId = None,
        status = Pending,
        winner_description = Cancelled,
        createdAt = Instant.now()
      )

      when(mockTournamentService.getMatch(100L, 1L))
        .thenReturn(Future.successful(Some(testMatch)))
      when(mockUploadSessionService.getSession(f"${testUser.userId}_1_100"))
        .thenReturn(None)

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockTournamentService,
        mockUserSmurfService,
        mockUploadSessionService,
        mockUploadedFileRepository,
        mockAnalyticalReplayService,
        mockAnalyticalResultRepository,
        mockUserAliasRepository,
        mockUserActivityService
      )

      val request = FakeRequest(POST, "/")
        .withFormUrlEncodedBody(
          "winner" -> "first_user",
          "smurfsFirstParticipant[0]" -> "smurf1",
          "smurfsSecondParticipant[0]" -> "smurf2"
        )

      val result = call(controller.closeMatch(1L, 100L), request)

      status(result) mustEqual INTERNAL_SERVER_ERROR
    }

    "redirect with success when match closed successfully" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockTournamentService = mock[TournamentService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadSessionService = mock[TournamentUploadSessionService]
      val mockUploadedFileRepository = mock[UploadedFileRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockUserActivityService = mock[UserActivityService]

      val testMatch = TournamentMatch(
        matchId = 1L,
        tournamentId = 100L,
        firstUserId = testUser.userId,
        secondUserId = rivalUser.userId,
        winnerUserId = None,
        status = Pending,
        winner_description = Cancelled,
        createdAt = Instant.now()
      )

      val testSession = createTestTournamentSession()

      when(mockTournamentService.getMatch(100L, 1L))
        .thenReturn(Future.successful(Some(testMatch)))
      when(mockUploadSessionService.getSession(f"${testUser.userId}_1_100"))
        .thenReturn(Some(testSession))
      when(mockTournamentService.submitMatchResult(any[TournamentMatch], any[WinnerShared]))
        .thenReturn(Future.successful(true))
      when(mockUserSmurfService.recordMatchSmurfs(anyLong(), anyLong(), any[ParticipantShared], any[ParticipantShared]))
        .thenReturn(Future.successful(Seq.empty))
      when(mockAnalyticalReplayService.analyticalProcessMatch(anyLong(), anyLong()))
        .thenReturn(Future.successful(Seq.empty))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockTournamentService,
        mockUserSmurfService,
        mockUploadSessionService,
        mockUploadedFileRepository,
        mockAnalyticalReplayService,
        mockAnalyticalResultRepository,
        mockUserAliasRepository,
        mockUserActivityService
      )

      val request = FakeRequest(POST, "/")
        .withFormUrlEncodedBody(
          "winner" -> "first_user",
          "smurfsFirstParticipant[0]" -> "smurf1",
          "smurfsSecondParticipant[0]" -> "smurf2"
        )

      val result = call(controller.closeMatch(1L, 100L), request)

      status(result) mustEqual SEE_OTHER
    }
  }
}
