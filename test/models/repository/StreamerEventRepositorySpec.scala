package models.repository

import models.StreamerEvent
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

class StreamerEventRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

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
  lazy val db = app.injector.instanceOf[DBApi].database("default")
  
  // Test data
  val testUserId = 1L
  val testUserName = "Test User"
  val testChannelId1 = "UC123456789"
  val testChannelId2 = "UC987654321"
  val testEventName1 = "Test Event 1"
  val testEventName2 = "Test Event 2"
  val testEventType = "LIVE"
  val testDescription = "Test event description"

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
          
          --- Add test data for StreamerEventRepositorySpec
          INSERT INTO users (user_id, user_name) VALUES (1, 'Test User');
          INSERT INTO yt_streamer (channel_id, onwer_user_id, current_balance_number) 
            VALUES ('UC123456789', 1, 0);
          INSERT INTO yt_streamer (channel_id, onwer_user_id, current_balance_number) 
            VALUES ('UC987654321', 1, 0);
          """,
          """
          --- !Downs
          
          --- Remove test data
          DELETE FROM streamer_events WHERE channel_id IN ('UC123456789', 'UC987654321');
          DELETE FROM yt_streamer WHERE channel_id IN ('UC123456789', 'UC987654321');
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

  "StreamerEventRepository" should {
    "create a new streamer event" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // Create a new event
      val event = StreamerEvent(
        None,
        testChannelId1,
        testEventName1,
        Some(testDescription),
        testEventType,
        0,
        true,
        Instant.now(),
        None,
        None,
        None
      )
      
      val createdF = repository.create(event)
      val created = Await.result(createdF, 5.seconds)
      
      // Verify the result
      created.eventId must not be None
      created.channelId must be(testChannelId1)
      created.eventName must be(testEventName1)
      created.eventDescription must be(Some(testDescription))
      created.eventType must be(testEventType)
      created.isActive must be(true)
      created.createdAt must not be None
      created.updatedAt must not be None
    }
    
    "get event by ID" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // Create an event
      val event = StreamerEvent(
        None,
        testChannelId1,
        testEventName1,
        Some(testDescription),
        testEventType,
        0,
        true,
        Instant.now(),
        None,
        None,
        None
      )
      
      val created = Await.result(repository.create(event), 5.seconds)
      
      // Get event by ID
      val retrievedF = repository.getById(created.eventId.get)
      val retrieved = Await.result(retrievedF, 5.seconds)
      
      // Verify result
      retrieved must not be None
      retrieved.get.eventId must be(created.eventId)
      retrieved.get.channelId must be(testChannelId1)
      retrieved.get.eventName must be(testEventName1)
    }
    
    "return None when getting a non-existent event" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // Get non-existent event
      val nonExistentF = repository.getById(999)
      val nonExistent = Await.result(nonExistentF, 5.seconds)
      
      // Verify result
      nonExistent must be(None)
    }
    
    "get events by channel ID" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // Create multiple events for the same channel
      val event1 = StreamerEvent(None, testChannelId1, testEventName1, None, testEventType, 0, true, Instant.now(), None, None, None)
      val event2 = StreamerEvent(None, testChannelId1, testEventName2, None, testEventType, 0, true, Instant.now(), None, None, None)
      
      Await.result(repository.create(event1), 5.seconds)
      Await.result(repository.create(event2), 5.seconds)
      
      // Create an event for another channel
      val event3 = StreamerEvent(None, testChannelId2, "Other event", None, testEventType, 0, true, Instant.now(), None, None, None)
      Await.result(repository.create(event3), 5.seconds)
      
      // Get events for channel 1
      val eventsF = repository.getByChannelId(testChannelId1)
      val events = Await.result(eventsF, 5.seconds)
      
      // Verify results
      events.size must be(2)
      events.map(_.eventName) must contain allOf(testEventName1, testEventName2)
      events.foreach(_.channelId must be(testChannelId1))
    }
    
    "get active events by channel ID" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // Create active and inactive events for the same channel
      val activeEvent = StreamerEvent(None, testChannelId1, testEventName1, None, testEventType, 0, true, Instant.now(), None, None, None)
      val inactiveEvent = StreamerEvent(None, testChannelId1, testEventName2, None, testEventType, 0, false, Instant.now(), Some(Instant.now()), None, None)
      
      Await.result(repository.create(activeEvent), 5.seconds)
      Await.result(repository.create(inactiveEvent), 5.seconds)
      
      // Get active events for channel 1
      val eventsF = repository.getActiveByChannelId(testChannelId1)
      val events = Await.result(eventsF, 5.seconds)
      
      // Verify results
      events.size must be(1)
      events.head.eventName must be(testEventName1)
      events.head.isActive must be(true)
    }
    

    "update an event" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // Create an event
      val event = StreamerEvent(None, testChannelId1, testEventName1, None, testEventType, 0, true, Instant.now(), None, None, None)
      val created = Await.result(repository.create(event), 5.seconds)
      
      // Update the event
      val updatedEvent = created.copy(
        eventName = "Updated name",
        eventDescription = Some("Updated description")
      )
      
      val updateResultF = repository.update(updatedEvent)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify update worked
      updateResult must be(1) // 1 row affected
      
      // Get the event to confirm update
      val retrievedF = repository.getById(created.eventId.get)
      val retrieved = Await.result(retrievedF, 5.seconds)
      
      retrieved must not be None
      retrieved.get.eventName must be("Updated name")
      retrieved.get.eventDescription must be(Some("Updated description"))
      retrieved.get.updatedAt must not be retrieved.get.createdAt
    }
    
    "end an event" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // Create an active event
      val event = StreamerEvent(None, testChannelId1, testEventName1, None, testEventType, 0, true, Instant.now(), None, None, None)
      val created = Await.result(repository.create(event), 5.seconds)
      
      // End the event
      val endResultF = repository.endEvent(created.eventId.get)
      val endResult = Await.result(endResultF, 5.seconds)
      
      // Verify end worked
      endResult must be(1) // 1 row affected
      
      // Get the event to confirm it's ended
      val retrievedF = repository.getById(created.eventId.get)
      val retrieved = Await.result(retrievedF, 5.seconds)
      
      retrieved must not be None
      retrieved.get.isActive must be(false)
      retrieved.get.endTime must not be None
    }
    
    "delete an event" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // Create an event
      val event = StreamerEvent(None, testChannelId1, testEventName1, None, testEventType, 0, true, Instant.now(), None, None, None)
      val created = Await.result(repository.create(event), 5.seconds)
      
      // Verify the event exists before deletion
      val existsBeforeF = repository.getById(created.eventId.get)
      val existsBefore = Await.result(existsBeforeF, 5.seconds)
      existsBefore must not be None
      
      // Delete the event
      val deleteResultF = repository.delete(created.eventId.get)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify delete worked
      deleteResult must be(1) // 1 row affected
      
      // Check that the event no longer exists
      val existsAfterF = repository.getById(created.eventId.get)
      val existsAfter = Await.result(existsAfterF, 5.seconds)
      existsAfter must be(None)
    }
    
    "get the most recent active event for a streamer" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // We'll need to create events with different start times
      val now = Instant.now()
      val olderTime = now.minusSeconds(3600) // 1 hour ago
      // Create multiple active events with different start times
      val olderEvent = StreamerEvent(None, testChannelId1, "Older event", None, testEventType, 0, true, olderTime, None, None, None)
      val newerEvent = StreamerEvent(None, testChannelId1, "Newer event", None, testEventType, 0, true, now, None, None, None)
      
      Await.result(repository.create(olderEvent), 5.seconds)
      Await.result(repository.create(newerEvent), 5.seconds)
      
      // Get the most recent active event
      val recentF = repository.getMostRecentActiveEvent(testChannelId1)
      val recent = Await.result(recentF, 5.seconds)
      
      // Verify result
      recent must not be None
      recent.get.eventName must be("Newer event")
      Math.abs(recent.get.startTime.getEpochSecond - now.getEpochSecond) must be <= 1L
    }
    
    "return None when no active events for streamer" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // Create an inactive event
      val inactiveEvent = StreamerEvent(None, testChannelId1, testEventName1, None, testEventType, 0, false, Instant.now(), Some(Instant.now()), None, None)
      Await.result(repository.create(inactiveEvent), 5.seconds)
      
      // Try to get active event
      val recentF = repository.getMostRecentActiveEvent(testChannelId1)
      val recent = Await.result(recentF, 5.seconds)
      
      // Verify no active events found
      recent must be(None)
    }
    
    "throw exception when updating event without ID" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // Attempt to update an event without an ID
      val invalidEvent = StreamerEvent(None, testChannelId1, testEventName1, None, testEventType, 0, true, Instant.now(), None, None, None)
      
      // Should throw an exception
      an[IllegalArgumentException] must be thrownBy {
        Await.result(repository.update(invalidEvent), 5.seconds)
      }
    }
    
    "handle end for non-existent event" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // End a non-existent event
      val endResultF = repository.endEvent(999)
      val endResult = Await.result(endResultF, 5.seconds)
      
      // Verify no rows affected
      endResult must be(0)
    }
    
    "handle delete for non-existent event" in {
      val repository = new StreamerEventRepository(dbConfigProvider, ytStreamerRepository)
      
      // Delete a non-existent event
      val deleteResultF = repository.delete(999)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify no rows affected
      deleteResult must be(0)
    }
  }
}
