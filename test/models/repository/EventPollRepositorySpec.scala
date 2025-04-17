package models.repository

import models.{EventPoll, StreamerEvent}
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

class EventPollRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

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
  lazy val db = app.injector.instanceOf[DBApi].database("default")
  
  // Test data
  val testUserId = 1L
  val testUserName = "Test User"
  val testChannelId = "UC123456789"
  val testEventId = 1
  val testEventName = "Test Event"
  val testEventType = "LIVE"
  val testPollId = 1
  val testPollQuestion = "Test question?"
  val anotherPollQuestion = "Another question?"

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
          
          --- Add test data for EventPollRepositorySpec
          INSERT INTO users (user_id, user_name) VALUES (1, 'Test User');
          INSERT INTO yt_streamer (channel_id, onwer_user_id, current_balance_number) 
            VALUES ('UC123456789', 1, 0);
          INSERT INTO streamer_events (event_id, channel_id, event_name, event_type, current_confidence_amount, is_active, start_time)
            VALUES (1, 'UC123456789', 'Test Event', 'LIVE', 0, true, CURRENT_TIMESTAMP);
          """,
          """
          --- !Downs
          
          --- Remove test data
          DELETE FROM streamer_events WHERE event_id = 1;
          DELETE FROM yt_streamer WHERE channel_id = 'UC123456789';
          DELETE FROM users WHERE user_id = 1;
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

  "EventPollRepository" should {
    "create a new event poll" in {
      val repository = new EventPollRepository(dbConfigProvider, streamerEventRepository)
      
      // Create a new poll
      val poll = EventPoll(None, testEventId, testPollQuestion)
      val createdF = repository.create(poll)
      
      val created = Await.result(createdF, 5.seconds)
      
      // Verify the result
      created.pollId must not be None
      created.eventId must be(testEventId)
      created.pollQuestion must be(testPollQuestion)
    }
    
    "get poll by ID" in {
      val repository = new EventPollRepository(dbConfigProvider, streamerEventRepository)
      
      // Create a poll
      val poll = EventPoll(None, testEventId, testPollQuestion)
      val created = Await.result(repository.create(poll), 5.seconds)
      
      // Get poll by ID
      val retrievedF = repository.getById(created.pollId.get)
      val retrieved = Await.result(retrievedF, 5.seconds)
      
      // Verify result
      retrieved must not be None
      retrieved.get.pollId must be(created.pollId)
      retrieved.get.eventId must be(testEventId)
      retrieved.get.pollQuestion must be(testPollQuestion)
    }
    
    "return None when getting a non-existent poll" in {
      val repository = new EventPollRepository(dbConfigProvider, streamerEventRepository)
      
      // Get non-existent poll
      val nonExistentF = repository.getById(999)
      val nonExistent = Await.result(nonExistentF, 5.seconds)
      
      // Verify result
      nonExistent must be(None)
    }
    
    "get all polls for an event" in {
      val repository = new EventPollRepository(dbConfigProvider, streamerEventRepository)
      
      // Create multiple polls for the same event
      val poll1 = EventPoll(None, testEventId, testPollQuestion)
      val poll2 = EventPoll(None, testEventId, anotherPollQuestion)
      
      Await.result(repository.create(poll1), 5.seconds)
      Await.result(repository.create(poll2), 5.seconds)
      
      // Get all polls for the event
      val pollsF = repository.getByEventId(testEventId)
      val polls = Await.result(pollsF, 5.seconds)
      
      // Verify results
      polls.size must be(2)
      polls.map(_.pollQuestion) must contain allOf(testPollQuestion, anotherPollQuestion)
    }
    
    "update a poll" in {
      val repository = new EventPollRepository(dbConfigProvider, streamerEventRepository)
      
      // Create a poll
      val poll = EventPoll(None, testEventId, testPollQuestion)
      val created = Await.result(repository.create(poll), 5.seconds)
      
      // Update the poll question
      val updatedPoll = created.copy(pollQuestion = "Updated question?")
      val updateResultF = repository.update(updatedPoll)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify update worked
      updateResult must be(1) // 1 row affected
      
      // Get the poll to confirm update
      val retrievedF = repository.getById(created.pollId.get)
      val retrieved = Await.result(retrievedF, 5.seconds)
      
      retrieved must not be None
      retrieved.get.pollQuestion must be("Updated question?")
    }
    
    "handle update for non-existent poll" in {
      val repository = new EventPollRepository(dbConfigProvider, streamerEventRepository)
      
      // Update a non-existent poll
      val nonExistentPoll = EventPoll(Some(999), testEventId, "Non-existent question?")
      val updateResultF = repository.update(nonExistentPoll)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify no rows affected
      updateResult must be(0)
    }
    
    "delete a poll" in {
      val repository = new EventPollRepository(dbConfigProvider, streamerEventRepository)
      
      // Create a poll
      val poll = EventPoll(None, testEventId, testPollQuestion)
      val created = Await.result(repository.create(poll), 5.seconds)
      
      // Verify the poll exists before deletion
      val existsBeforeF = repository.getById(created.pollId.get)
      val existsBefore = Await.result(existsBeforeF, 5.seconds)
      existsBefore must not be None
      
      // Delete the poll
      val deleteResultF = repository.delete(created.pollId.get)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify delete worked
      deleteResult must be(1) // 1 row affected
      
      // Check that the poll no longer exists
      val existsAfterF = repository.getById(created.pollId.get)
      val existsAfter = Await.result(existsAfterF, 5.seconds)
      existsAfter must be(None)
    }
    
    "handle delete for non-existent poll" in {
      val repository = new EventPollRepository(dbConfigProvider, streamerEventRepository)
      
      // Delete a non-existent poll
      val deleteResultF = repository.delete(999)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify no rows affected
      deleteResult must be(0)
    }
    
    "delete all polls for an event" in {
      val repository = new EventPollRepository(dbConfigProvider, streamerEventRepository)
      
      // Create multiple polls for the same event
      val poll1 = EventPoll(None, testEventId, testPollQuestion)
      val poll2 = EventPoll(None, testEventId, anotherPollQuestion)
      
      Await.result(repository.create(poll1), 5.seconds)
      Await.result(repository.create(poll2), 5.seconds)
      
      // Verify polls exist before deletion
      val existsBeforeF = repository.getByEventId(testEventId)
      val existsBefore = Await.result(existsBeforeF, 5.seconds)
      existsBefore.size must be(2)
      
      // Delete all polls for the event
      val deleteResultF = repository.deleteByEventId(testEventId)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify delete worked
      deleteResult must be(2) // 2 rows affected
      
      // Check that no polls exist for the event now
      val existsAfterF = repository.getByEventId(testEventId)
      val existsAfter = Await.result(existsAfterF, 5.seconds)
      existsAfter must be(empty)
    }
    
    "handle deleteByEventId for non-existent event" in {
      val repository = new EventPollRepository(dbConfigProvider, streamerEventRepository)
      
      // Delete polls for a non-existent event
      val deleteResultF = repository.deleteByEventId(999)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify no rows affected
      deleteResult must be(0)
    }
    
    "throw exception when updating poll without ID" in {
      val repository = new EventPollRepository(dbConfigProvider, streamerEventRepository)
      
      // Attempt to update a poll without an ID
      val invalidPoll = EventPoll(None, testEventId, testPollQuestion)
      
      // Should throw an exception
      an[IllegalArgumentException] must be thrownBy {
        Await.result(repository.update(invalidPoll), 5.seconds)
      }
    }
  }
}
