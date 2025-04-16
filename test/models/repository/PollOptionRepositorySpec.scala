package models.repository

import models.{EventPoll, PollOption}
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

class PollOptionRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

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
  val testOptionText1 = "Option 1"
  val testOptionText2 = "Option 2"
  val testOptionText3 = "Option 3"

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
          
          --- Add test data for PollOptionRepositorySpec
          INSERT INTO users (user_id, user_name) VALUES (1, 'Test User');
          INSERT INTO yt_streamer (channel_id, onwer_user_id, current_balance_number) 
            VALUES ('UC123456789', 1, 0);
          INSERT INTO streamer_events (event_id, channel_id, event_name, event_type, is_active, start_time) 
            VALUES (1, 'UC123456789', 'Test Event', 'LIVE', true, CURRENT_TIMESTAMP);
          INSERT INTO event_polls (poll_id, event_id, poll_question) 
            VALUES (1, 1, 'Test question?');
          """,
          """
          --- !Downs
          
          --- Remove test data
          DELETE FROM event_polls WHERE poll_id = 1;
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

  "PollOptionRepository" should {
    "create a new poll option" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Create a new option
      val option = PollOption(None, testPollId, testOptionText1)
      val createdF = repository.create(option)
      
      val created = Await.result(createdF, 5.seconds)
      
      // Verify the result
      created.optionId must not be None
      created.pollId must be(testPollId)
      created.optionText must be(testOptionText1)
    }
    
    "create multiple poll options at once" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Create multiple options at once
      val optionTexts = Seq(testOptionText1, testOptionText2, testOptionText3)
      val createdF = repository.createMultiple(testPollId, optionTexts)
      
      val created = Await.result(createdF, 5.seconds)
      
      // Verify the results
      created.size must be(3)
      created.map(_.pollId).distinct must be(Seq(testPollId))
      created.map(_.optionText) must contain allOf(testOptionText1, testOptionText2, testOptionText3)
      created.foreach(_.optionId must not be None)
    }
    
    "get option by ID" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Create an option
      val option = PollOption(None, testPollId, testOptionText1)
      val created = Await.result(repository.create(option), 5.seconds)
      
      // Get option by ID
      val retrievedF = repository.getById(created.optionId.get)
      val retrieved = Await.result(retrievedF, 5.seconds)
      
      // Verify result
      retrieved must not be None
      retrieved.get.optionId must be(created.optionId)
      retrieved.get.pollId must be(testPollId)
      retrieved.get.optionText must be(testOptionText1)
    }
    
    "return None when getting a non-existent option" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Get non-existent option
      val nonExistentF = repository.getById(999)
      val nonExistent = Await.result(nonExistentF, 5.seconds)
      
      // Verify result
      nonExistent must be(None)
    }
    
    "get all options for a poll" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Create multiple options for the same poll
      val optionTexts = Seq(testOptionText1, testOptionText2, testOptionText3)
      Await.result(repository.createMultiple(testPollId, optionTexts), 5.seconds)
      
      // Get all options for the poll
      val optionsF = repository.getByPollId(testPollId)
      val options = Await.result(optionsF, 5.seconds)
      
      // Verify results
      options.size must be(3)
      options.map(_.optionText) must contain allOf(testOptionText1, testOptionText2, testOptionText3)
    }
    
    "update an option" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Create an option
      val option = PollOption(None, testPollId, testOptionText1)
      val created = Await.result(repository.create(option), 5.seconds)
      
      // Update the option text
      val updatedOption = created.copy(optionText = "Updated option")
      val updateResultF = repository.update(updatedOption)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify update worked
      updateResult must be(1) // 1 row affected
      
      // Get the option to confirm update
      val retrievedF = repository.getById(created.optionId.get)
      val retrieved = Await.result(retrievedF, 5.seconds)
      
      retrieved must not be None
      retrieved.get.optionText must be("Updated option")
    }
    
    "handle update for non-existent option" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Update a non-existent option
      val nonExistentOption = PollOption(Some(999), testPollId, "Non-existent option")
      val updateResultF = repository.update(nonExistentOption)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify no rows affected
      updateResult must be(0)
    }
    
    "delete an option" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Create an option
      val option = PollOption(None, testPollId, testOptionText1)
      val created = Await.result(repository.create(option), 5.seconds)
      
      // Verify the option exists before deletion
      val existsBeforeF = repository.getById(created.optionId.get)
      val existsBefore = Await.result(existsBeforeF, 5.seconds)
      existsBefore must not be None
      
      // Delete the option
      val deleteResultF = repository.delete(created.optionId.get)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify delete worked
      deleteResult must be(1) // 1 row affected
      
      // Check that the option no longer exists
      val existsAfterF = repository.getById(created.optionId.get)
      val existsAfter = Await.result(existsAfterF, 5.seconds)
      existsAfter must be(None)
    }
    
    "handle delete for non-existent option" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Delete a non-existent option
      val deleteResultF = repository.delete(999)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify no rows affected
      deleteResult must be(0)
    }
    
    "delete all options for a poll" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Create multiple options for the same poll
      val optionTexts = Seq(testOptionText1, testOptionText2, testOptionText3)
      Await.result(repository.createMultiple(testPollId, optionTexts), 5.seconds)
      
      // Verify options exist before deletion
      val existsBeforeF = repository.getByPollId(testPollId)
      val existsBefore = Await.result(existsBeforeF, 5.seconds)
      existsBefore.size must be(3)
      
      // Delete all options for the poll
      val deleteResultF = repository.deleteByPollId(testPollId)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify delete worked
      deleteResult must be(3) // 3 rows affected
      
      // Check that no options exist for the poll now
      val existsAfterF = repository.getByPollId(testPollId)
      val existsAfter = Await.result(existsAfterF, 5.seconds)
      existsAfter must be(empty)
    }
    
    "handle deleteByPollId for non-existent poll" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Delete options for a non-existent poll
      val deleteResultF = repository.deleteByPollId(999)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify no rows affected
      deleteResult must be(0)
    }
    
    "throw exception when updating option without ID" in {
      val repository = new PollOptionRepository(dbConfigProvider, eventPollRepository)
      
      // Attempt to update an option without an ID
      val invalidOption = PollOption(None, testPollId, testOptionText1)
      
      // Should throw an exception
      an[IllegalArgumentException] must be thrownBy {
        Await.result(repository.update(invalidOption), 5.seconds)
      }
    }
  }
}
