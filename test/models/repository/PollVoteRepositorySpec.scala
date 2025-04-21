package models.repository

import models.{EventPoll, PollOption, PollVote}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.db.evolutions.{Evolution, Evolutions, SimpleEvolutionsReader}
import play.api.db.slick.DatabaseConfigProvider
import play.api.db.{DBApi, Database}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.*
import play.api.test.Injecting

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import java.time.Instant

class PollVoteRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

  // We'll use an in-memory H2 database for testing
  override def fakeApplication(): Application = {
    GuiceApplicationBuilder()
      .configure(
        "slick.dbs.default.profile" -> "slick.jdbc.H2Profile$",
        "slick.dbs.default.db.driver" -> "org.h2.Driver",
        "slick.dbs.default.db.url" -> "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "slick.dbs.default.db.user" -> "sa",
        "slick.dbs.default.db.password" -> "",
        // Makes sure evolutions are enabled for tests
        "play.evolutions.db.default.enabled" -> true,
        "play.evolutions.db.default.autoApply" -> true
      )
      .build()
  }

  // Components we need throughout the test
  lazy val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
  lazy val userRepository = app.injector.instanceOf[UserRepository]
  lazy val ytStreamerRepository = app.injector.instanceOf[YtStreamerRepository]
  lazy val streamerEventRepository = app.injector.instanceOf[StreamerEventRepository]
  lazy val eventPollRepository = app.injector.instanceOf[EventPollRepository]
  lazy val pollOptionRepository = app.injector.instanceOf[PollOptionRepository]
  lazy val db = app.injector.instanceOf[DBApi].database("default")
  
  // Test data
  val testUserId1 = 1L
  val testUserId2 = 2L
  val testUserId3 = 3L
  val testUserName1 = "Test User 1"
  val testUserName2 = "Test User 2"
  val testChannelId = "UC123456789"
  val testEventId = 1
  val testEventName = "Test Event"
  val testPollId = 1
  val testPollQuestion = "Test question?"
  val testOptionId1 = 1
  val testOptionId2 = 2
  val testOptionText1 = "Option 1"
  val testOptionText2 = "Option 2"
  val testConfidenceAmount = 100
  val testMessage = "Test message"

  override def beforeEach(): Unit = {
    super.beforeEach()
    
    // Apply the standard evolutions
    Evolutions.applyEvolutions(db)
    
    // Add test-specific data
    Evolutions.applyEvolutions(db,
      SimpleEvolutionsReader.forDefault(
        Evolution(
          20,
          """
          --- !Ups
          
          --- Add test data for PollVoteRepositorySpec
          INSERT INTO users (user_id, user_name) VALUES (1, 'Test User 1');
          INSERT INTO users (user_id, user_name) VALUES (2, 'Test User 2');
          INSERT INTO users (user_id, user_name) VALUES (3, 'Test User 3');
          INSERT INTO yt_streamer (channel_id, onwer_user_id, current_balance_number)
            VALUES ('UC123456789', 1, 0);
          INSERT INTO streamer_events (event_id, channel_id, event_name, event_type, current_confidence_amount, is_active, start_time)
            VALUES (1, 'UC123456789', 'Test Event', 'LIVE', 0, true, CURRENT_TIMESTAMP);
          INSERT INTO event_polls (poll_id, event_id, poll_question) 
            VALUES (1, 1, 'Test question?');
          INSERT INTO poll_options (option_id, poll_id, option_text) 
            VALUES (1, 1, 'Option 1');
          INSERT INTO poll_options (option_id, poll_id, option_text) 
            VALUES (2, 1, 'Option 2');
          """,
          """
          --- !Downs
          
          --- Remove test data
          DELETE FROM poll_options WHERE poll_id = 1;
          DELETE FROM event_polls WHERE poll_id = 1;
          DELETE FROM streamer_events WHERE event_id = 1;
          DELETE FROM yt_streamer WHERE channel_id = 'UC123456789';
          DELETE FROM users WHERE user_id IN (1, 2);
          """
        )
      )
    )
  }

  override def afterEach(): Unit = {
    // Clean up the database after each test
    Evolutions.cleanupEvolutions(db)
    
    super.afterEach()
  }

  "PollVoteRepository" should {
    "create a new vote" in {
      val repository = new PollVoteRepository(dbConfigProvider, pollOptionRepository, userRepository)
      
      // Create a new vote
      val vote = PollVote(None, testPollId, testOptionId1, testUserId1, Some(testMessage), testConfidenceAmount, None)
      val createdF = repository.create(vote)
      
      val created = Await.result(createdF, 5.seconds)
      
      // Verify the result
      created.voteId must not be None
      created.pollId must be(testPollId)
      created.optionId must be(testOptionId1)
      created.userId must be(testUserId1)
      created.confidenceAmount must be(testConfidenceAmount)
      created.messageByChatOpt must be(Some(testMessage))
      created.createdAt must not be None
    }
    
    "get vote by ID" in {
      val repository = new PollVoteRepository(dbConfigProvider, pollOptionRepository, userRepository)
      
      // Create a vote
      val vote = PollVote(None, testPollId, testOptionId1, testUserId1, Some(testMessage), testConfidenceAmount, None)
      val created = Await.result(repository.create(vote), 5.seconds)
      
      // Get vote by ID
      val retrievedF = repository.getById(created.voteId.get)
      val retrieved = Await.result(retrievedF, 5.seconds)
      
      // Verify result
      retrieved must not be None
      retrieved.get.voteId must be(created.voteId)
      retrieved.get.pollId must be(testPollId)
      retrieved.get.optionId must be(testOptionId1)
      retrieved.get.userId must be(testUserId1)
      retrieved.get.confidenceAmount must be(testConfidenceAmount)
      retrieved.get.messageByChatOpt must be(Some(testMessage))
    }
    
    "return None when getting a non-existent vote" in {
      val repository = new PollVoteRepository(dbConfigProvider, pollOptionRepository, userRepository)
      
      // Get non-existent vote
      val nonExistentF = repository.getById(999)
      val nonExistent = Await.result(nonExistentF, 5.seconds)
      
      // Verify result
      nonExistent must be(None)
    }
    
    "get all votes for a poll" in {
      val repository = new PollVoteRepository(dbConfigProvider, pollOptionRepository, userRepository)
      
      // Create multiple votes for the poll
      val vote1 = PollVote(None, testPollId, testOptionId1, testUserId1, Some(testMessage), testConfidenceAmount, None )
      val vote2 = PollVote(None, testPollId, testOptionId2, testUserId2, Some("Another message"), testConfidenceAmount * 2, None)
      
      Await.result(repository.create(vote1), 5.seconds)
      Await.result(repository.create(vote2), 5.seconds)
      
      // Get all votes for the poll
      val votesF = repository.getByPollId(testPollId)
      val votes = Await.result(votesF, 5.seconds)
      
      // Verify results
      votes.size must be(2)
      votes.map(_.userId) must contain allOf(testUserId1, testUserId2)
      votes.map(_.optionId) must contain allOf(testOptionId1, testOptionId2)
    }
    
    "get all votes for a poll option" in {
      val repository = new PollVoteRepository(dbConfigProvider, pollOptionRepository, userRepository)
      
      // Create multiple votes for different options
      val vote1 = PollVote(None, testPollId, testOptionId1, testUserId1, None, testConfidenceAmount, None)
      val vote2 = PollVote(None, testPollId, testOptionId2, testUserId2, None, testConfidenceAmount * 2, None)
      
      Await.result(repository.create(vote1), 5.seconds)
      Await.result(repository.create(vote2), 5.seconds)
      
      // Get votes for option 1
      val votesF = repository.getByOptionId(testOptionId1)
      val votes = Await.result(votesF, 5.seconds)
      
      // Verify results
      votes.size must be(1)
      votes.head.userId must be(testUserId1)
      votes.head.optionId must be(testOptionId1)
    }
    
    "get votes by user and poll" in {
      val repository = new PollVoteRepository(dbConfigProvider, pollOptionRepository, userRepository)
      
      // Create votes for different users
      val vote1 = PollVote(None, testPollId, testOptionId1, testUserId1, None, testConfidenceAmount, None)
      val vote2 = PollVote(None, testPollId, testOptionId2, testUserId2, None, testConfidenceAmount * 2, None)
      
      Await.result(repository.create(vote1), 5.seconds)
      Await.result(repository.create(vote2), 5.seconds)
      
      // Get votes for user 1 in the poll
      val votesF = repository.getByUserAndPoll(testUserId1, testPollId)
      val votes = Await.result(votesF, 5.seconds)
      
      // Verify results
      votes.size must be(1)
      votes.head.userId must be(testUserId1)
      votes.head.pollId must be(testPollId)
    }
    
    "check if a user has voted in a poll" in {
      val repository = new PollVoteRepository(dbConfigProvider, pollOptionRepository, userRepository)
      
      // Create a vote for user 1
      val vote = PollVote(None, testPollId, testOptionId1, testUserId1, None, testConfidenceAmount, None)
      Await.result(repository.create(vote), 5.seconds)
      
      // Check if user 1 has voted
      val hasVotedUser1F = repository.hasUserVotedInPoll(testUserId1, testPollId)
      val hasVotedUser1 = Await.result(hasVotedUser1F, 5.seconds)
      
      // Check if user 2 has voted
      val hasVotedUser2F = repository.hasUserVotedInPoll(testUserId2, testPollId)
      val hasVotedUser2 = Await.result(hasVotedUser2F, 5.seconds)
      
      // Verify results
      hasVotedUser1 must be(true)
      hasVotedUser2 must be(false)
    }
    
    "delete a vote" in {
      val repository = new PollVoteRepository(dbConfigProvider, pollOptionRepository, userRepository)
      
      // Create a vote
      val vote = PollVote(None, testPollId, testOptionId1, testUserId1, None, testConfidenceAmount, None)
      val created = Await.result(repository.create(vote), 5.seconds)
      
      // Verify the vote exists before deletion
      val existsBeforeF = repository.getById(created.voteId.get)
      val existsBefore = Await.result(existsBeforeF, 5.seconds)
      existsBefore must not be None
      
      // Delete the vote
      val deleteResultF = repository.delete(created.voteId.get)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify delete worked
      deleteResult must be(1) // 1 row affected
      
      // Check that the vote no longer exists
      val existsAfterF = repository.getById(created.voteId.get)
      val existsAfter = Await.result(existsAfterF, 5.seconds)
      existsAfter must be(None)
    }
    
    "delete all votes for a poll" in {
      val repository = new PollVoteRepository(dbConfigProvider, pollOptionRepository, userRepository)
      
      // Create multiple votes for the poll
      val vote1 = PollVote(None, testPollId, testOptionId1, testUserId1, None, testConfidenceAmount, None)
      val vote2 = PollVote(None, testPollId, testOptionId2, testUserId2, None, testConfidenceAmount * 2, None)
      
      Await.result(repository.create(vote1), 5.seconds)
      Await.result(repository.create(vote2), 5.seconds)
      
      // Verify votes exist before deletion
      val existsBeforeF = repository.getByPollId(testPollId)
      val existsBefore = Await.result(existsBeforeF, 5.seconds)
      existsBefore.size must be(2)
      
      // Delete all votes for the poll
      val deleteResultF = repository.deleteByPollId(testPollId)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify delete worked
      deleteResult must be(2) // 2 rows affected
      
      // Check that no votes exist for the poll now
      val existsAfterF = repository.getByPollId(testPollId)
      val existsAfter = Await.result(existsAfterF, 5.seconds)
      existsAfter must be(empty)
    }
    
    "count votes by option" in {
      val repository = new PollVoteRepository(dbConfigProvider, pollOptionRepository, userRepository)
      
      // Create multiple votes for different options
      val vote1 = PollVote(None, testPollId, testOptionId1, testUserId1, None, testConfidenceAmount, None)
      val vote2 = PollVote(None, testPollId, testOptionId1, testUserId2, None, testConfidenceAmount * 2, None)
      val vote3 = PollVote(None, testPollId, testOptionId2, testUserId3, None, testConfidenceAmount, None)
      
      Await.result(repository.create(vote1), 5.seconds)
      Await.result(repository.create(vote2), 5.seconds)
      Await.result(repository.create(vote3), 5.seconds)
      
      // Count votes by option
      val countsF = repository.countVotesByOption(testPollId)
      val counts = Await.result(countsF, 5.seconds)
      
      // Verify counts
      counts.size must be(2)
      counts(testOptionId1) must be(2)
      counts(testOptionId2) must be(1)
    }
    
    "sum confidence by option" in {
      val repository = new PollVoteRepository(dbConfigProvider, pollOptionRepository, userRepository)
      
      // Create multiple votes with different confidence amounts
      val vote1 = PollVote(None, testPollId, testOptionId1, testUserId1, None, 100, None)
      val vote2 = PollVote(None, testPollId, testOptionId1, testUserId2, None, 200, None)
      val vote3 = PollVote(None, testPollId, testOptionId2, testUserId3, None, 150, None)
      
      Await.result(repository.create(vote1), 5.seconds)
      Await.result(repository.create(vote2), 5.seconds)
      Await.result(repository.create(vote3), 5.seconds)
      
      // Sum confidence by option
      val sumsF = repository.sumConfidenceByOption(testPollId)
      val sums = Await.result(sumsF, 5.seconds)
      
      // Verify sums
      sums.size must be(2)
      sums(testOptionId1) must be(300L)
      sums(testOptionId2) must be(150L)
    }
  }
}
