package actors

import models.*
import models.repository.*
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import org.apache.pekko.actor.typed.ActorRef
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.db.DBApi
import play.api.db.evolutions.{Evolution, Evolutions, SimpleEvolutionsReader}
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.Helpers.*
import services.{ActiveLiveStream, ChatService, InferUserOptionService, PollService}

import java.time.Instant
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext

class YoutubeLiveChatPollingActorSpec extends PlaySpec
  with GuiceOneAppPerSuite
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MockitoSugar {

  // Configure the test ActorSystem
  private val testKit = ActorTestKit()

  // Mock dependencies
  private val mockWsClient = mock[WSClient]
  private val mockWSRequest = mock[play.api.libs.ws.WSRequest]
  private val mockWSResponse = mock[WSResponse]
  private val mockChatService = mock[ChatService]
  private val mockInferUserOptionService = mock[InferUserOptionService]

  // Test data
  private val apiKey = "test-api-key"
  private val testLiveChatId = "live-chat-123"
  private val testChannelId = "channel-456"
  private val testStreamEventId = 1
  private val testStreamEventName = "Test Stream Event"
  private val testPollId = 1
  private val testPollQuestion = "Test Poll Question"
  private val testOptionId1 = 1
  private val testOptionId2 = 2
  private val testOptionText1 = "Option 1"
  private val testOptionText2 = "Option 2"
  private val testUserId = 1L
  private val testUserName = "Test User"
  private val testUserChannelId = "user-channel-123"
  private val testStartTime = Instant.now().minusSeconds(3600) // 1 hour ago

  // In-memory H2 database for testing
  override def fakeApplication(): Application = {
    GuiceApplicationBuilder()
      .configure(
        "slick.dbs.default.profile" -> "slick.jdbc.H2Profile$",
        "slick.dbs.default.db.driver" -> "org.h2.Driver",
        "slick.dbs.default.db.url" -> "jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "slick.dbs.default.db.user" -> "sa",
        "slick.dbs.default.db.password" -> "",
        // Makes sure evolutions are enabled for tests
        "play.evolutions.db.default.enabled" -> true,
        "play.evolutions.db.default.autoApply" -> true
      )
      .build()
  }

  // Components we need from the app
  lazy val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
  lazy val userRepository = app.injector.instanceOf[UserRepository]
  lazy val ytUserRepository = app.injector.instanceOf[YtUserRepository]
  lazy val ytStreamerRepository = app.injector.instanceOf[YtStreamerRepository]
  lazy val userStreamerStateRepository = app.injector.instanceOf[UserStreamerStateRepository]
  lazy val streamerEventRepository = app.injector.instanceOf[StreamerEventRepository]
  lazy val eventPollRepository = app.injector.instanceOf[EventPollRepository]
  lazy val pollOptionRepository = app.injector.instanceOf[PollOptionRepository]
  lazy val pollVoteRepository = app.injector.instanceOf[PollVoteRepository]
  lazy val pollService = app.injector.instanceOf[PollService]
  lazy val liveStream = app.injector.instanceOf[ActiveLiveStream]
  lazy val db = app.injector.instanceOf[DBApi].database("default")

  // Create a real service but with mocked dependencies for testing
  lazy val inferUserOptionService = new InferUserOptionService(mockWsClient)(ExecutionContext.global)

  // Setup before each test
  override def beforeEach(): Unit = {
    super.beforeEach()

    // Reset all mocks
    reset(mockWsClient, mockWSRequest, mockWSResponse, mockChatService, mockInferUserOptionService)

    // Apply the standard evolutions
    Evolutions.applyEvolutions(db)

    // Add test-specific data
    Evolutions.applyEvolutions(db,
      SimpleEvolutionsReader.forDefault(
        Evolution(
          20,
          """
          --- !Ups

          --- Test data setup for YoutubeLiveChatPollingActorSpec

          -- Create test user
          INSERT INTO users (user_name)
            VALUES ('Test User');

          -- Create test YouTube user
          INSERT INTO yt_users (user_channel_id, user_id, display_name, activated)
            VALUES ('user-channel-123', 1, 'Test User', true);

          -- Create test streamer
          INSERT INTO yt_streamer (channel_id, onwer_user_id, current_balance_number)
            VALUES ('channel-456', 1, 0);

          -- Create test streamer event
          INSERT INTO streamer_events (event_id, channel_id, event_name, event_type, current_confidence_amount, is_active, start_time)
            VALUES (1, 'channel-456', 'Test Stream Event', 'LIVE', 0, true, CURRENT_TIMESTAMP);

          -- Create test poll
          INSERT INTO event_polls (poll_id, event_id, poll_question)
            VALUES (1, 1, 'Test Poll Question');

          -- Create test poll options
          INSERT INTO poll_options (option_id, poll_id, option_text, confidence_ratio)
            VALUES (1, 1, 'Option 1', 1.5);

          INSERT INTO poll_options (option_id, poll_id, option_text, confidence_ratio)
            VALUES (2, 1, 'Option 2', 1.5);

          -- Create user-streamer state
          INSERT INTO user_streamer_state (user_id, streamer_channel_id, current_balance_number)
            VALUES (1, 'channel-456', 1000);
          """,
          """
          --- !Downs

          --- Remove test data
          DELETE FROM user_streamer_state WHERE 1 = 1;
          DELETE FROM yt_streamer WHERE 1 = 1;
          DELETE FROM poll_options WHERE 1=1;
          DELETE FROM event_polls WHERE 1=1;
          DELETE FROM streamer_events WHERE 1=1;
          DELETE FROM yt_users WHERE 1 = 1;
          DELETE FROM users WHERE 1 = 1;
          """
        )
      )
    )

    // Setup mock WS client
    when(mockWsClient.url(anyString())).thenReturn(mockWSRequest)
    when(mockWSRequest.withQueryStringParameters(any())).thenReturn(mockWSRequest)
    when(mockWSRequest.get()).thenReturn(Future.successful(mockWSResponse))
  }

  override def afterEach(): Unit = {
    Evolutions.cleanupEvolutions(db)
    super.afterEach()
  }
  // Cleanup after all tests
  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  "YoutubeLiveChatPollingActor" should {
    "process messages and register poll votes when valid YouTube chat messages are received" in {
      // 1. Create test probe to receive responses
      val probe = testKit.createTestProbe[YoutubeLiveChatPollingActor.Command]()

      // 2. Setup mock response from YouTube API
      val now = Instant.now()
      val mockJsonResponse = Json.obj(
        "nextPageToken" -> "next-page-token-123",
        "pollingIntervalMillis" -> 60000,
        "items" -> Json.arr(
          Json.obj(
            "id" -> "message-id-1",
            "snippet" -> Json.obj(
              "displayMessage" -> s"I choose $testOptionText1 with 50 points",
              "publishedAt" -> now.plusSeconds(10).toString
            ),
            "authorDetails" -> Json.obj(
              "channelId" -> testUserChannelId,
              "displayName" -> testUserName
            )
          ),
          Json.obj(
            "id" -> "message-id-2",
            "snippet" -> Json.obj(
              "displayMessage" -> "This is a random message",
              "publishedAt" -> now.plusSeconds(20).toString
            ),
            "authorDetails" -> Json.obj(
              "channelId" -> "another-user-123",
              "displayName" -> "Another User"
            )
          ),
          Json.obj(
            "id" -> "message-id-3",
            "snippet" -> Json.obj(
              "displayMessage" -> s"I think $testOptionText2 is correct with 0",
              "publishedAt" -> now.plusSeconds(30).toString
            ),
            "authorDetails" -> Json.obj(
              "channelId" -> "third-user-456",
              "displayName" -> "Third User"
            )
          )
        )
      )

      when(mockWSResponse.json).thenReturn(mockJsonResponse)

      // 3. Mock the InferUserOptionService to return responses for different messages
      when(mockInferUserOptionService.inferencePollResponse(
        any[EventPoll],
        any[List[PollOption]],
        org.mockito.ArgumentMatchers.eq(s"I choose $testOptionText1 with 50 points")
      )).thenReturn(Future.successful(
        Some((PollOption(Some(testOptionId1), testPollId, testOptionText1, 1.5), 50))
      ))

      when(mockInferUserOptionService.inferencePollResponse(
        any[EventPoll],
        any[List[PollOption]],
        org.mockito.ArgumentMatchers.eq("This is a random message")
      )).thenReturn(Future.successful(None))

      when(mockInferUserOptionService.inferencePollResponse(
        any[EventPoll],
        any[List[PollOption]],
        org.mockito.ArgumentMatchers.eq(s"I think $testOptionText2 is correct with 0")
      )).thenReturn(Future.successful(
        Some((PollOption(Some(testOptionId2), testPollId, testOptionText2, 1.5), 0))
      ))

      // 4. Create actor instance with real repositories but mocked services
      val actor = testKit.spawn(YoutubeLiveChatPollingActor(
        mockWsClient,
        apiKey,
        ytStreamerRepository,
        userStreamerStateRepository,
        userRepository,
        ytUserRepository,
        testStartTime,
        pollService,
        mockInferUserOptionService,
        mockChatService,
        pollVoteRepository,
        liveStream
      ))

      // 5. Send PollLiveChat command to start the polling process
      actor ! YoutubeLiveChatPollingActor.PollLiveChat(testLiveChatId, testChannelId, null, 0)

      // 6. Allow some time for processing
      Thread.sleep(3000)

      // 7. Verify that the expected interactions occurred
      verify(mockWsClient).url("https://www.googleapis.com/youtube/v3/liveChat/messages")
      verify(mockWSRequest).withQueryStringParameters(any())
      verify(mockWSRequest).get()

      // 8. Verify that new users were created in the database for users not already in the system
      // The test data includes user-channel-123, but the other two should be created
      val anotherUser = Await.result(ytUserRepository.getByChannelId("another-user-123"), 5.seconds)
      val thirdUser = Await.result(ytUserRepository.getByChannelId("third-user-456"), 5.seconds)

      anotherUser must not be None
      thirdUser must not be None

      // 9. Verify chat service broadcasts
      verify(mockChatService, atLeastOnce).broadcastMessage(anyString(), anyString(), any())

      // 10. Verify poll votes were registered
      val pollVotes = Await.result(pollVoteRepository.getByPollId(testPollId), 5.seconds)
      pollVotes.size must be >= 2

      // There should be at least one vote for each option
      pollVotes.count(_.optionId == testOptionId1) must be >= 1
      pollVotes.count(_.optionId == testOptionId2) must be >= 1

      // Check confidence amounts
      val optionOneVotes = pollVotes.filter(_.optionId == testOptionId1)
      val optionTwoVotes = pollVotes.filter(_.optionId == testOptionId2)

      optionOneVotes.exists(_.confidenceAmount == 50) must be(true)
      optionTwoVotes.exists(_.confidenceAmount == 0) must be(true)
    }

    "handle API errors gracefully with retry mechanism" in {
      // 1. Create test probe to receive responses
      val probe = testKit.createTestProbe[YoutubeLiveChatPollingActor.Command]()

      // 2. Setup mock WS client to throw an exception
      when(mockWSRequest.get()).thenReturn(Future.failed(new RuntimeException("API Error")))

      // 3. Create actor instance
      val actor = testKit.spawn(YoutubeLiveChatPollingActor(
        mockWsClient,
        apiKey,
        ytStreamerRepository,
        userStreamerStateRepository,
        userRepository,
        ytUserRepository,
        testStartTime,
        pollService,
        mockInferUserOptionService,
        mockChatService,
        pollVoteRepository,
        liveStream
      ))

      // 4. Send PollLiveChat command to start the polling process
      actor ! YoutubeLiveChatPollingActor.PollLiveChat(testLiveChatId, testChannelId, null, 0)

      // 5. Allow some time for processing
      Thread.sleep(1000)

      // 6. Verify that retry was attempted (error handling logic worked)
      verify(mockWsClient).url("https://www.googleapis.com/youtube/v3/liveChat/messages")
      verify(mockWSRequest).withQueryStringParameters(any())
      verify(mockWSRequest).get()

      // The actor should not crash, and it should schedule a retry
      // But we can't easily verify the timer scheduling in this test
    }

    "handle empty poll results properly" in {
      // 1. Create test probe to receive responses
      val probe = testKit.createTestProbe[YoutubeLiveChatPollingActor.Command]()

      // 2. Setup mock response from YouTube API
      val now = Instant.now()
      val mockJsonResponse = Json.obj(
        "nextPageToken" -> "next-page-token-123",
        "pollingIntervalMillis" -> 60000,
        "items" -> Json.arr(
          Json.obj(
            "id" -> "message-id-1",
            "snippet" -> Json.obj(
              "displayMessage" -> "Hello everyone!",
              "publishedAt" -> now.plusSeconds(10).toString
            ),
            "authorDetails" -> Json.obj(
              "channelId" -> testUserChannelId,
              "displayName" -> testUserName
            )
          )
        )
      )

      when(mockWSResponse.json).thenReturn(mockJsonResponse)

      // 3. Mock the InferUserOptionService to return no match
      when(mockInferUserOptionService.inferencePollResponse(
        any[EventPoll],
        any[List[PollOption]],
        anyString()
      )).thenReturn(Future.successful(None))

      // 4. Create actor instance
      val actor = testKit.spawn(YoutubeLiveChatPollingActor(
        mockWsClient,
        apiKey,
        ytStreamerRepository,
        userStreamerStateRepository,
        userRepository,
        ytUserRepository,
        testStartTime,
        pollService,
        mockInferUserOptionService,
        mockChatService,
        pollVoteRepository,
        liveStream
      ))

      // 5. Send PollLiveChat command to start the polling process
      actor ! YoutubeLiveChatPollingActor.PollLiveChat(testLiveChatId, testChannelId, null, 0)

      // 6. Allow some time for processing
      Thread.sleep(1000)

      // 7. Verify that message was processed but no poll vote was registered
      verify(mockWsClient).url("https://www.googleapis.com/youtube/v3/liveChat/messages")
      verify(mockWSRequest).withQueryStringParameters(any())
      verify(mockWSRequest).get()
      verify(mockInferUserOptionService).inferencePollResponse(any(), any(), anyString())

      // 8. Verify no poll votes were registered
      val pollVotes = Await.result(pollVoteRepository.getByPollId(testPollId), 5.seconds)
      pollVotes.isEmpty must be(true)

      // 9. Verify chat service broadcasts the message without poll information
      verify(mockChatService).broadcastMessage(contains("[  ]"), anyString(), any())
    }

    "register a new user when the message author does not exist in the system" in {
      // 1. Create test probe to receive responses
      val probe = testKit.createTestProbe[YoutubeLiveChatPollingActor.Command]()

      // 2. Setup mock response from YouTube API with a new user
      val now = Instant.now()
      val newUserChannelId = "new-user-789"
      val newUserDisplayName = "New User"

      val mockJsonResponse = Json.obj(
        "nextPageToken" -> "next-page-token-123",
        "pollingIntervalMillis" -> 60000,
        "items" -> Json.arr(
          Json.obj(
            "id" -> "message-id-1",
            "snippet" -> Json.obj(
              "displayMessage" -> s"I vote for $testOptionText1 with 0 points",
              "publishedAt" -> now.plusSeconds(10).toString
            ),
            "authorDetails" -> Json.obj(
              "channelId" -> newUserChannelId,
              "displayName" -> newUserDisplayName
            )
          )
        )
      )

      when(mockWSResponse.json).thenReturn(mockJsonResponse)

      // 3. Mock the InferUserOptionService to return a match
      when(mockInferUserOptionService.inferencePollResponse(
        any[EventPoll],
        any[List[PollOption]],
        anyString()
      )).thenReturn(Future.successful(
        Some((PollOption(Some(testOptionId1), testPollId, testOptionText1, 1.5), 0))
      ))

      // 4. Create actor instance
      val actor = testKit.spawn(YoutubeLiveChatPollingActor(
        mockWsClient,
        apiKey,
        ytStreamerRepository,
        userStreamerStateRepository,
        userRepository,
        ytUserRepository,
        testStartTime,
        pollService,
        mockInferUserOptionService,
        mockChatService,
        pollVoteRepository,
        liveStream
      ))

      // 5. Verify the user doesn't exist before the test
      val userBefore = Await.result(ytUserRepository.getByChannelId(newUserChannelId), 5.seconds)
      userBefore must be(None)

      // 6. Send PollLiveChat command to start the polling process
      actor ! YoutubeLiveChatPollingActor.PollLiveChat(testLiveChatId, testChannelId, null, 0)

      // 7. Allow some time for processing
      Thread.sleep(2000)

      // 8. Verify that a new user was created
      val userAfter = Await.result(ytUserRepository.getByChannelId(newUserChannelId), 5.seconds)
      userAfter must not be None
      userAfter.get.displayName must be(Some(newUserDisplayName))

      // 9. Verify that the user-streamer relationship was created
      val userStreamerState = Await.result(
        userStreamerStateRepository.exists(userAfter.get.userId, testChannelId),
        5.seconds
      )
      userStreamerState must be(true)

      // 10. Verify that a poll vote was registered for this user
      val pollVotes = Await.result(pollVoteRepository.getByPollId(testPollId), 5.seconds)
      pollVotes.exists(vote => vote.userId == userAfter.get.userId && vote.optionId == testOptionId1) must be(true)
    }
  }
}
