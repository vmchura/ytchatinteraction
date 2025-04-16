package models.repository

import models.YtStreamer
import models.repository.{UserRepository, YtStreamerRepository}
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

class YtStreamerRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

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
  lazy val db = app.injector.instanceOf[DBApi].database("default")
  
  // Test data
  val testUserId1 = 1L
  val testUserId2 = 2L
  val testUserName1 = "Test User 1"
  val testUserName2 = "Test User 2"
  val testChannelId1 = "UC123456789"
  val testChannelId2 = "UC987654321"
  val testChannelId3 = "UC_another"
  val initialBalance = 10

  override def beforeEach(): Unit = {
    super.beforeEach()
    
    // Apply the standard evolutions
    Evolutions.applyEvolutions(db)
    
    // Add test-specific data - only users, we'll create streamers in tests
    Evolutions.applyEvolutions(db,
      SimpleEvolutionsReader.forDefault(
        Evolution(
          2,
          """
          --- !Ups
          
          --- Add test data for YtStreamerRepositorySpec
          INSERT INTO users (user_id, user_name) VALUES (1, 'Test User 1');
          INSERT INTO users (user_id, user_name) VALUES (2, 'Test User 2');
          """,
          s"""
          --- !Downs
          
          --- Remove test data
          DELETE FROM yt_streamer WHERE channel_id IN ('$testChannelId1', '$testChannelId2', '$testChannelId3');
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

  "YtStreamerRepository" should {
    "create a new YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Create a new YouTube streamer
      val createdF = repository.create(testChannelId1, Some(testUserId1), initialBalance)

      val created = Await.result(createdF, 5.seconds)
      // Verify the result
      created.channelId must be(testChannelId1)
      created.ownerUserId must be(Some(testUserId1))
      created.currentBalanceNumber must be(initialBalance)
    }
    
    "create a new YouTube streamer with default balance (0)" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Create with default balance
      val createdF = repository.create(testChannelId1, Some(testUserId1))
      val created = Await.result(createdF, 5.seconds)
      
      // Verify the result
      created.channelId must be(testChannelId1)
      created.ownerUserId must be(Some(testUserId1))
      created.currentBalanceNumber must be(0)
    }
    
    "list all YouTube streamers" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Create multiple YouTube streamers
      Await.result(repository.create(testChannelId1, Some(testUserId1), 5), 5.seconds)
      Await.result(repository.create(testChannelId2, Some(testUserId2), 10), 5.seconds)
      
      // List all YouTube streamers
      val streamersF = repository.list()
      val streamers = Await.result(streamersF, 5.seconds)
      
      // Verify the results
      streamers.size must be(2)
      streamers.map(_.channelId) must contain allOf(testChannelId1, testChannelId2)
      streamers.map(s => (s.channelId, s.ownerUserId)) must contain allOf(
        (testChannelId1, Some(testUserId1)),
        (testChannelId2, Some(testUserId2))
      )
    }
    
    "get YouTube streamer by channel ID" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Create a YouTube streamer
      Await.result(repository.create(testChannelId1, Some(testUserId1), initialBalance), 5.seconds)
      
      // Get streamer by channel ID
      val streamerF = repository.getByChannelId(testChannelId1)
      val streamer = Await.result(streamerF, 5.seconds)
      
      // Verify the result
      streamer must not be None
      streamer.get.channelId must be(testChannelId1)
      streamer.get.ownerUserId must be(Some(testUserId1))
      streamer.get.currentBalanceNumber must be(initialBalance)
    }
    
    "return None when getting a non-existent YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Get non-existent streamer by channel ID
      val streamerF = repository.getByChannelId("NonExistentChannelId")
      val streamer = Await.result(streamerF, 5.seconds)
      
      // Verify the result
      streamer must be(None)
    }
    
    "get YouTube streamers by owner user ID" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Create multiple YouTube streamers for the same owner
      Await.result(repository.create(testChannelId1, Some(testUserId1)), 5.seconds)
      Await.result(repository.create(testChannelId2, Some(testUserId1)), 5.seconds)
      Await.result(repository.create(testChannelId3, Some(testUserId2)), 5.seconds)
      
      // Get streamers by owner user ID
      val streamersF = repository.getByOwnerUserId(testUserId1)
      val streamers = Await.result(streamersF, 5.seconds)
      
      // Verify the results
      streamers.size must be(2)
      streamers.map(_.channelId) must contain allOf(testChannelId1, testChannelId2)
      streamers.foreach(_.ownerUserId must be(Some(testUserId1)))
    }
    
    "update a YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Create a YouTube streamer
      Await.result(repository.create(testChannelId1, Some(testUserId1), 5), 5.seconds)
      
      // Update the streamer
      val updatedStreamer = YtStreamer(testChannelId1, Some(testUserId2), 15)
      val updateResultF = repository.update(updatedStreamer)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify the update worked
      updateResult must be(1) // 1 row affected
      
      // Get the streamer to confirm the update
      val streamerF = repository.getByChannelId(testChannelId1)
      val streamer = Await.result(streamerF, 5.seconds)
      
      streamer must not be None
      streamer.get.ownerUserId must be(Some(testUserId2))
      streamer.get.currentBalanceNumber must be(15)
    }
    
    "update balance for a YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Create a YouTube streamer with initial balance
      Await.result(repository.create(testChannelId1, Some(testUserId1), initialBalance), 5.seconds)
      
      // Update the balance
      val newBalance = 25
      val updateResultF = repository.updateBalance(testChannelId1, newBalance)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify the update worked
      updateResult must be(1) // 1 row affected
      
      // Get the balance to confirm the update
      val balanceF = repository.getBalance(testChannelId1)
      val balance = Await.result(balanceF, 5.seconds)
      
      balance must be(newBalance)
    }
    
    "increment balance for a YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Create a YouTube streamer with initial balance
      Await.result(repository.create(testChannelId1, Some(testUserId1), initialBalance), 5.seconds)
      
      // Increment the balance by default amount (1)
      val incrementResultF = repository.incrementBalance(testChannelId1)
      val incrementResult = Await.result(incrementResultF, 5.seconds)
      
      // Verify the increment worked
      incrementResult must be(1) // 1 row affected
      
      // Get the balance to confirm the increment
      val balanceF = repository.getBalance(testChannelId1)
      val balance = Await.result(balanceF, 5.seconds)
      
      balance must be(initialBalance + 1)
      
      // Increment the balance by a specific amount
      val incrementAmount = 5
      val incrementAgainF = repository.incrementBalance(testChannelId1, incrementAmount)
      val incrementAgain = Await.result(incrementAgainF, 5.seconds)
      
      // Verify the second increment worked
      incrementAgain must be(1) // 1 row affected
      
      // Get the balance to confirm the second increment
      val newBalanceF = repository.getBalance(testChannelId1)
      val newBalance = Await.result(newBalanceF, 5.seconds)
      
      newBalance must be(initialBalance + 1 + incrementAmount)
    }
    
    "get balance for a YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Create a YouTube streamer with initial balance
      Await.result(repository.create(testChannelId1, Some(testUserId1), initialBalance), 5.seconds)
      
      // Get the balance
      val balanceF = repository.getBalance(testChannelId1)
      val balance = Await.result(balanceF, 5.seconds)
      
      // Verify the balance
      balance must be(initialBalance)
    }
    
    "return 0 when getting balance for non-existent YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Get balance for non-existent streamer
      val balanceF = repository.getBalance("NonExistentChannelId")
      val balance = Await.result(balanceF, 5.seconds)
      
      // Verify the balance is 0 for non-existent streamer
      balance must be(0)
    }
    
    "delete a YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Create a YouTube streamer
      Await.result(repository.create(testChannelId1, Some(testUserId1)), 5.seconds)
      
      // Verify the streamer exists before deletion
      val existsBeforeF = repository.getByChannelId(testChannelId1)
      val existsBefore = Await.result(existsBeforeF, 5.seconds)
      existsBefore must not be None
      
      // Delete the streamer
      val deleteResultF = repository.delete(testChannelId1)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify the delete worked
      deleteResult must be(1) // 1 row affected
      
      // Check that the streamer no longer exists
      val existsAfterF = repository.getByChannelId(testChannelId1)
      val existsAfter = Await.result(existsAfterF, 5.seconds)
      existsAfter must be(None)
    }
    
    "handle update for non-existent YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Update a non-existent streamer
      val nonExistentStreamer = YtStreamer("NonExistentChannelId", Some(testUserId1), 50)
      val updateResultF = repository.update(nonExistentStreamer)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify no rows were affected
      updateResult must be(0)
    }
    
    "handle updateBalance for non-existent YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Update balance for a non-existent streamer
      val updateBalanceF = repository.updateBalance("NonExistentChannelId", 50)
      val updateBalance = Await.result(updateBalanceF, 5.seconds)
      
      // Verify no rows were affected
      updateBalance must be(0)
    }
    
    "handle incrementBalance for non-existent YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Increment balance for a non-existent streamer
      val incrementBalanceF = repository.incrementBalance("NonExistentChannelId", 5)
      val incrementBalance = Await.result(incrementBalanceF, 5.seconds)
      
      // Since incrementBalance creates the action transactionally,
      // it should update 0 rows for a non-existent streamer
      incrementBalance must be(0)
    }
    
    "handle delete for non-existent YouTube streamer" in {
      val repository = new YtStreamerRepository(dbConfigProvider, userRepository)
      
      // Delete a non-existent streamer
      val deleteResultF = repository.delete("NonExistentChannelId")
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify no rows were affected
      deleteResult must be(0)
    }
  }
}