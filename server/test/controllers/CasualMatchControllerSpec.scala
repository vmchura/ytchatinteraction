package controllers

import evolutioncomplete._
import evolutioncomplete.WinnerShared.Cancelled
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

class CasualMatchControllerSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  implicit val actorSystem: ActorSystem = ActorSystem("test")
  implicit val materializer: Materializer = Materializer(actorSystem)

  val testUser: User = User(1L, "testUser")
  val rivalUser: User = User(2L, "rivalUser")

  def createController(
                        mockSilhouette: Silhouette[DefaultEnv],
                        mockUploadSessionService: CasualMatchUploadSessionService,
                        mockFileStorageService: FileStorageService,
                        mockParseReplayFileService: ParseReplayFileService,
                        mockUserSmurfService: UserSmurfService,
                        mockUploadedFileRepository: CasualMatchFileRepository,
                        mockCasualMatchRepository: CasualMatchRepository,
                        mockAnalyticalReplayService: AnalyticalReplayService,
                        mockUserAliasRepository: UserAliasRepository,
                        mockAnalyticalResultRepository: AnalyticalResultRepository,
                        mockUserActivityService: UserActivityService
                      ): CasualMatchController = {
    val mockComponents = mock[DefaultSilhouetteControllerComponents]
    when(mockComponents.silhouette).thenReturn(mockSilhouette)
    when(mockComponents.messagesApi).thenReturn(stubMessagesApi())
    when(mockComponents.langs).thenReturn(stubLangs())
    when(mockComponents.fileMimeTypes).thenReturn(stubControllerComponents().fileMimeTypes)
    when(mockComponents.executionContext).thenReturn(global)
    when(mockComponents.actionBuilder).thenReturn(stubControllerComponents().actionBuilder)
    when(mockComponents.parsers).thenReturn(stubControllerComponents().parsers)

    new CasualMatchController(
      mockComponents,
      mockUploadSessionService,
      mockFileStorageService,
      mockParseReplayFileService,
      mockUserSmurfService,
      mockUploadedFileRepository,
      mockCasualMatchRepository,
      mockAnalyticalReplayService,
      mockUserAliasRepository,
      mockAnalyticalResultRepository,
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

    when(mockSecuredAction.apply(any[play.silhouette.api.actions.SecuredRequest[DefaultEnv, AnyContent] => Result])).thenAnswer { invocation =>
      val func = invocation.getArgument[play.silhouette.api.actions.SecuredRequest[DefaultEnv, AnyContent] => Result](0)
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
        Future.successful(func(securedRequest))
      }
    }

    mockSecuredAction
  }

  def createTestCasualMatchSession(): CasualMatchSession = {
    CasualMatchSession(
      userId = testUser.userId,
      casualMatchId = 1L,
      uploadState = CasualMatchStateShared(
        casualMatchId = 1L,
        firstParticipant = ParticipantShared(testUser.userId, testUser.userName, Set.empty),
        secondParticipant = ParticipantShared(rivalUser.userId, rivalUser.userName, Set.empty),
        games = Nil,
        winner = Cancelled
      ),
      hash2StoreInformation = Map.empty,
      lastUpdated = Instant.now()
    )
  }

  "CasualMatchController#fetchState" should {
    "return Ok with state when session exists" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUploadSessionService = mock[CasualMatchUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadedFileRepository = mock[CasualMatchFileRepository]
      val mockCasualMatchRepository = mock[CasualMatchRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserActivityService = mock[UserActivityService]

      val testSession = createTestCasualMatchSession()
      when(mockUploadSessionService.getOrCreateSession(any[MetaCasualMatchSession]))
        .thenReturn(Future.successful(Some(testSession)))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockUploadSessionService,
        mockFileStorageService,
        mockParseReplayFileService,
        mockUserSmurfService,
        mockUploadedFileRepository,
        mockCasualMatchRepository,
        mockAnalyticalReplayService,
        mockUserAliasRepository,
        mockAnalyticalResultRepository,
        mockUserActivityService
      )

      val request = FakeRequest(GET, "/")
      val result = call(controller.fetchState(1L), request)

      status(result) mustEqual OK
    }

    "return BadRequest when session does not exist" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUploadSessionService = mock[CasualMatchUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadedFileRepository = mock[CasualMatchFileRepository]
      val mockCasualMatchRepository = mock[CasualMatchRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserActivityService = mock[UserActivityService]

      when(mockUploadSessionService.getOrCreateSession(any[MetaCasualMatchSession]))
        .thenReturn(Future.successful(None))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockUploadSessionService,
        mockFileStorageService,
        mockParseReplayFileService,
        mockUserSmurfService,
        mockUploadedFileRepository,
        mockCasualMatchRepository,
        mockAnalyticalReplayService,
        mockUserAliasRepository,
        mockAnalyticalResultRepository,
        mockUserActivityService
      )

      val request = FakeRequest(GET, "/")
      val result = call(controller.fetchState(1L), request)

      status(result) mustEqual BAD_REQUEST
    }
  }

  "CasualMatchController#uploadFormForMatch" should {
    "return Ok with upload form" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUploadSessionService = mock[CasualMatchUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadedFileRepository = mock[CasualMatchFileRepository]
      val mockCasualMatchRepository = mock[CasualMatchRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserActivityService = mock[UserActivityService]

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockUploadSessionService,
        mockFileStorageService,
        mockParseReplayFileService,
        mockUserSmurfService,
        mockUploadedFileRepository,
        mockCasualMatchRepository,
        mockAnalyticalReplayService,
        mockUserAliasRepository,
        mockAnalyticalResultRepository,
        mockUserActivityService
      )

      val request = CSRFTokenHelper.addCSRFToken(FakeRequest(GET, "/"))
      val result = call(controller.uploadFormForMatch(1L), request)

      status(result) mustEqual OK
    }
  }

  "CasualMatchController#viewFindUser" should {
    "return Ok with user list" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUploadSessionService = mock[CasualMatchUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadedFileRepository = mock[CasualMatchFileRepository]
      val mockCasualMatchRepository = mock[CasualMatchRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserActivityService = mock[UserActivityService]

      when(mockUserAliasRepository.list())
        .thenReturn(Future.successful(Seq(
          UserAliasHistory(1L, rivalUser.userId, rivalUser.userName, true, Instant.now(), None, "initial")
        )))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockUploadSessionService,
        mockFileStorageService,
        mockParseReplayFileService,
        mockUserSmurfService,
        mockUploadedFileRepository,
        mockCasualMatchRepository,
        mockAnalyticalReplayService,
        mockUserAliasRepository,
        mockAnalyticalResultRepository,
        mockUserActivityService
      )

      val request = CSRFTokenHelper.addCSRFToken(FakeRequest(GET, "/"))
      val result = call(controller.viewFindUser(), request)

      status(result) mustEqual OK
    }
  }

  "CasualMatchController#createCasualMatch" should {
    "redirect to upload form when session created" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUploadSessionService = mock[CasualMatchUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadedFileRepository = mock[CasualMatchFileRepository]
      val mockCasualMatchRepository = mock[CasualMatchRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserActivityService = mock[UserActivityService]

      val testCasualMatch = CasualMatch(1L, testUser.userId, rivalUser.userId, None, Instant.now(), Pending)
      when(mockCasualMatchRepository.create(any[CasualMatch]))
        .thenReturn(Future.successful(testCasualMatch))

      val testSession = createTestCasualMatchSession()
      when(mockUploadSessionService.startSession(any[MetaCasualMatchSession]))
        .thenReturn(Future.successful(Some(testSession)))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockUploadSessionService,
        mockFileStorageService,
        mockParseReplayFileService,
        mockUserSmurfService,
        mockUploadedFileRepository,
        mockCasualMatchRepository,
        mockAnalyticalReplayService,
        mockUserAliasRepository,
        mockAnalyticalResultRepository,
        mockUserActivityService
      )

      val request = FakeRequest(GET, "/")
      val result = call(controller.createCasualMatch(rivalUser.userId), request)

      status(result) mustEqual SEE_OTHER
    }

    "redirect to viewFindUser when session creation fails" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUploadSessionService = mock[CasualMatchUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadedFileRepository = mock[CasualMatchFileRepository]
      val mockCasualMatchRepository = mock[CasualMatchRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserActivityService = mock[UserActivityService]

      val testCasualMatch = CasualMatch(1L, testUser.userId, rivalUser.userId, None, Instant.now(), Pending)
      when(mockCasualMatchRepository.create(any[CasualMatch]))
        .thenReturn(Future.successful(testCasualMatch))

      when(mockUploadSessionService.startSession(any[MetaCasualMatchSession]))
        .thenReturn(Future.successful(None))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockUploadSessionService,
        mockFileStorageService,
        mockParseReplayFileService,
        mockUserSmurfService,
        mockUploadedFileRepository,
        mockCasualMatchRepository,
        mockAnalyticalReplayService,
        mockUserAliasRepository,
        mockAnalyticalResultRepository,
        mockUserActivityService
      )

      val request = FakeRequest(GET, "/")
      val result = call(controller.createCasualMatch(rivalUser.userId), request)

      status(result) mustEqual SEE_OTHER
    }
  }

  "CasualMatchController#removeFile" should {
    "return Ok when session exists" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUploadSessionService = mock[CasualMatchUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadedFileRepository = mock[CasualMatchFileRepository]
      val mockCasualMatchRepository = mock[CasualMatchRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserActivityService = mock[UserActivityService]

      val testSession = createTestCasualMatchSession()
      when(mockUploadSessionService.getOrCreateSession(any[MetaCasualMatchSession]))
        .thenReturn(Future.successful(Some(testSession)))
      when(mockUploadSessionService.removeFileFromSession(any[CasualMatchSession], any[UUID]))
        .thenReturn(testSession)
      when(mockUploadSessionService.persistState(any[CasualMatchSession]))
        .thenReturn(testSession)

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockUploadSessionService,
        mockFileStorageService,
        mockParseReplayFileService,
        mockUserSmurfService,
        mockUploadedFileRepository,
        mockCasualMatchRepository,
        mockAnalyticalReplayService,
        mockUserAliasRepository,
        mockAnalyticalResultRepository,
        mockUserActivityService
      )

      val request = FakeRequest(DELETE, "/")
      val result = call(controller.removeFile(1L, UUID.randomUUID()), request)

      status(result) mustEqual OK
    }

    "return BadRequest when session does not exist" in {
      val mockSilhouette = mock[Silhouette[DefaultEnv]]
      val mockUploadSessionService = mock[CasualMatchUploadSessionService]
      val mockFileStorageService = mock[FileStorageService]
      val mockParseReplayFileService = mock[ParseReplayFileService]
      val mockUserSmurfService = mock[UserSmurfService]
      val mockUploadedFileRepository = mock[CasualMatchFileRepository]
      val mockCasualMatchRepository = mock[CasualMatchRepository]
      val mockAnalyticalReplayService = mock[AnalyticalReplayService]
      val mockUserAliasRepository = mock[UserAliasRepository]
      val mockAnalyticalResultRepository = mock[AnalyticalResultRepository]
      val mockUserActivityService = mock[UserActivityService]

      when(mockUploadSessionService.getOrCreateSession(any[MetaCasualMatchSession]))
        .thenReturn(Future.successful(None))

      val mockSecuredAction = createMockSecuredAction(testUser)
      when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)

      val controller = createController(
        mockSilhouette,
        mockUploadSessionService,
        mockFileStorageService,
        mockParseReplayFileService,
        mockUserSmurfService,
        mockUploadedFileRepository,
        mockCasualMatchRepository,
        mockAnalyticalReplayService,
        mockUserAliasRepository,
        mockAnalyticalResultRepository,
        mockUserActivityService
      )

      val request = FakeRequest(DELETE, "/")
      val result = call(controller.removeFile(1L, UUID.randomUUID()), request)

      status(result) mustEqual BAD_REQUEST
    }
  }
}