package models.repository

import models.repository.{UserRepository, UserStreamerStateRepository, YtStreamerRepository}
import org.mockito.Mockito.when
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

class UserStreamerStateRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

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
  lazy val db = app.injector.instanceOf[DBApi].database("default")
  lazy val userRepository = app.injector.instanceOf[UserRepository]
  lazy val ytStreamerRepository = app.injector.instanceOf[YtStreamerRepository]
  
  // Test data
  val testUserId1 = 1L
  val testUserId2 = 2L
  val testUserName1 = "Test User 1"
  val testUserName2 = "Test User 2"
  val testChannelId1 = "UC123456789"
  val testChannelId2 = "UC987654321"
  val initialBalance = 10

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
          
          --- Add test data for UserStreamerStateRepositorySpec
          INSERT INTO users (user_id, user_name) VALUES (1, 'Test User 1');
          INSERT INTO users (user_id, user_name) VALUES (2, 'Test User 2');
          INSERT INTO yt_streamer (channel_id, onwer_user_id, current_balance_number) 
            VALUES ('UC123456789', 1, 0);
          INSERT INTO yt_streamer (channel_id, onwer_user_id, current_balance_number) 
            VALUES ('UC987654321', 2, 0);
          """,
          """
          --- !Downs
          
          --- Remove test data
          DELETE FROM user_streamer_state WHERE user_id IN (1, 2);
          DELETE FROM yt_streamer WHERE channel_id IN ('UC123456789', 'UC987654321');
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

  "UserStreamerStateRepository" should {
    "create a new user streamer state" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create a new user streamer state
      val createdF = repository.create(testUserId1, testChannelId1, initialBalance)
      val created = Await.result(createdF, 5.seconds)
      
      // Verify the result
      created.userId must be(testUserId1)
      created.streamerChannelId must be(testChannelId1)
      created.currentBalanceNumber must be(initialBalance)
    }
    
    "create a new user streamer state with default balance (0)" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create with default balance
      val createdF = repository.create(testUserId1, testChannelId1)
      val created = Await.result(createdF, 5.seconds)
      
      // Verify the result
      created.userId must be(testUserId1)
      created.streamerChannelId must be(testChannelId1)
      created.currentBalanceNumber must be(0)
    }
    
    "list all user streamer states" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create multiple user streamer states
      Await.result(repository.create(testUserId1, testChannelId1, 5), 5.seconds)
      Await.result(repository.create(testUserId1, testChannelId2, 10), 5.seconds)
      Await.result(repository.create(testUserId2, testChannelId1, 15), 5.seconds)
      
      // List all user streamer states
      val statesF = repository.list()
      val states = Await.result(statesF, 5.seconds)
      
      // Verify the results
      states.size must be(3)
      states.map(s => (s.userId, s.streamerChannelId)) must contain allOf(
        (testUserId1, testChannelId1),
        (testUserId1, testChannelId2),
        (testUserId2, testChannelId1)
      )
    }
    
    "get user streamer states by user ID" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create multiple user streamer states for the same user
      Await.result(repository.create(testUserId1, testChannelId1, 5), 5.seconds)
      Await.result(repository.create(testUserId1, testChannelId2, 10), 5.seconds)
      Await.result(repository.create(testUserId2, testChannelId1, 15), 5.seconds)
      
      // Get states by user ID
      val statesF = repository.getByUserId(testUserId1)
      val states = Await.result(statesF, 5.seconds)
      
      // Verify the results
      states.size must be(2)
      states.map(_.streamerChannelId) must contain allOf(testChannelId1, testChannelId2)
      states.foreach(_.userId must be(testUserId1))
    }
    
    "get user streamer states by streamer channel ID" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create multiple user streamer states for the same streamer
      Await.result(repository.create(testUserId1, testChannelId1, 5), 5.seconds)
      Await.result(repository.create(testUserId1, testChannelId2, 10), 5.seconds)
      Await.result(repository.create(testUserId2, testChannelId1, 15), 5.seconds)
      
      // Get states by streamer channel ID
      val statesF = repository.getByStreamerChannelId(testChannelId1)
      val states = Await.result(statesF, 5.seconds)
      
      // Verify the results
      states.size must be(2)
      states.map(_.userId) must contain allOf(testUserId1, testUserId2)
      states.foreach(_.streamerChannelId must be(testChannelId1))
    }
    
    "check if a user streamer state exists" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create a user streamer state
      Await.result(repository.create(testUserId1, testChannelId1), 5.seconds)
      
      // Check if state exists
      val existsF = repository.exists(testUserId1, testChannelId1)
      val exists = Await.result(existsF, 5.seconds)
      
      // Verify the state exists
      exists must be(true)
      
      // Check for non-existent state
      val nonExistentF = repository.exists(testUserId2, testChannelId2)
      val nonExistent = Await.result(nonExistentF, 5.seconds)
      
      // Verify the non-existent state doesn't exist
      nonExistent must be(false)
    }
    
    "update balance for a user streamer state" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create a user streamer state with initial balance
      Await.result(repository.create(testUserId1, testChannelId1, initialBalance), 5.seconds)
      
      // Update the balance
      val newBalance = 25
      val updateResultF = repository.updateBalance(testUserId1, testChannelId1, newBalance)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify the update worked
      updateResult must be(1) // 1 row affected
      
      // Get the balance to confirm the update
      val balanceF = repository.getBalance(testUserId1, testChannelId1)
      val balance = Await.result(balanceF, 5.seconds)
      
      balance must be(newBalance)
    }
    
    "increment balance for a user streamer state" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create a user streamer state with initial balance
      Await.result(repository.create(testUserId1, testChannelId1, initialBalance), 5.seconds)
      
      // Increment the balance by default amount (1)
      val incrementResultF = repository.incrementBalance(testUserId1, testChannelId1)
      val incrementResult = Await.result(incrementResultF, 5.seconds)
      
      // Verify the increment worked
      incrementResult must be(1) // 1 row affected
      
      // Get the balance to confirm the increment
      val balanceF = repository.getBalance(testUserId1, testChannelId1)
      val balance = Await.result(balanceF, 5.seconds)
      
      balance must be(initialBalance + 1)
      
      // Increment the balance by a specific amount
      val incrementAmount = 5
      val incrementAgainF = repository.incrementBalance(testUserId1, testChannelId1, incrementAmount)
      val incrementAgain = Await.result(incrementAgainF, 5.seconds)
      
      // Verify the second increment worked
      incrementAgain must be(1) // 1 row affected
      
      // Get the balance to confirm the second increment
      val newBalanceF = repository.getBalance(testUserId1, testChannelId1)
      val newBalance = Await.result(newBalanceF, 5.seconds)
      
      newBalance must be(initialBalance + 1 + incrementAmount)
    }
    
    "get balance for a user streamer state" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create a user streamer state with initial balance
      Await.result(repository.create(testUserId1, testChannelId1, initialBalance), 5.seconds)
      
      // Get the balance
      val balanceF = repository.getBalance(testUserId1, testChannelId1)
      val balance = Await.result(balanceF, 5.seconds)
      
      // Verify the balance
      balance must be(initialBalance)
    }
    
    "return 0 when getting balance for non-existent user streamer state" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Get balance for non-existent state
      val balanceF = repository.getBalance(testUserId2, testChannelId2)
      val balance = Await.result(balanceF, 5.seconds)
      
      // Verify the balance is 0 for non-existent state
      balance must be(0)
    }
    
    "delete a user streamer state" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create a user streamer state
      Await.result(repository.create(testUserId1, testChannelId1), 5.seconds)
      
      // Verify the state exists before deletion
      val existsBeforeF = repository.exists(testUserId1, testChannelId1)
      val existsBefore = Await.result(existsBeforeF, 5.seconds)
      existsBefore must be(true)
      
      // Delete the state
      val deleteResultF = repository.delete(testUserId1, testChannelId1)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify the delete worked
      deleteResult must be(1) // 1 row affected
      
      // Check that the state no longer exists
      val existsAfterF = repository.exists(testUserId1, testChannelId1)
      val existsAfter = Await.result(existsAfterF, 5.seconds)
      existsAfter must be(false)
    }
    
    "delete user streamer states by user ID" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create multiple user streamer states for the same user
      Await.result(repository.create(testUserId1, testChannelId1), 5.seconds)
      Await.result(repository.create(testUserId1, testChannelId2), 5.seconds)
      Await.result(repository.create(testUserId2, testChannelId1), 5.seconds)
      
      // Delete states by user ID
      val deleteResultF = repository.deleteByUserId(testUserId1)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify the delete worked
      deleteResult must be(2) // 2 rows affected
      
      // Check that the states for user 1 no longer exist
      val existsUser1Channel1F = repository.exists(testUserId1, testChannelId1)
      val existsUser1Channel1 = Await.result(existsUser1Channel1F, 5.seconds)
      existsUser1Channel1 must be(false)
      
      val existsUser1Channel2F = repository.exists(testUserId1, testChannelId2)
      val existsUser1Channel2 = Await.result(existsUser1Channel2F, 5.seconds)
      existsUser1Channel2 must be(false)
      
      // Check that the state for user 2 still exists
      val existsUser2Channel1F = repository.exists(testUserId2, testChannelId1)
      val existsUser2Channel1 = Await.result(existsUser2Channel1F, 5.seconds)
      existsUser2Channel1 must be(true)
    }
    
    "delete user streamer states by streamer channel ID" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Create multiple user streamer states for the same streamer
      Await.result(repository.create(testUserId1, testChannelId1), 5.seconds)
      Await.result(repository.create(testUserId1, testChannelId2), 5.seconds)
      Await.result(repository.create(testUserId2, testChannelId1), 5.seconds)
      
      // Delete states by streamer channel ID
      val deleteResultF = repository.deleteByStreamerChannelId(testChannelId1)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify the delete worked
      deleteResult must be(2) // 2 rows affected
      
      // Check that the states for channel 1 no longer exist
      val existsUser1Channel1F = repository.exists(testUserId1, testChannelId1)
      val existsUser1Channel1 = Await.result(existsUser1Channel1F, 5.seconds)
      existsUser1Channel1 must be(false)
      
      val existsUser2Channel1F = repository.exists(testUserId2, testChannelId1)
      val existsUser2Channel1 = Await.result(existsUser2Channel1F, 5.seconds)
      existsUser2Channel1 must be(false)
      
      // Check that the state for channel 2 still exists
      val existsUser1Channel2F = repository.exists(testUserId1, testChannelId2)
      val existsUser1Channel2 = Await.result(existsUser1Channel2F, 5.seconds)
      existsUser1Channel2 must be(true)
    }
    
    "handle update for non-existent user streamer state" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Update a non-existent state
      val updateResultF = repository.updateBalance(testUserId2, testChannelId2, 50)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify no rows were affected
      updateResult must be(0)
    }
    
    "handle delete for non-existent user streamer state" in {
      val repository = new UserStreamerStateRepository(dbConfigProvider, userRepository, ytStreamerRepository)
      
      // Delete a non-existent state
      val deleteResultF = repository.delete(testUserId2, testChannelId2)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify no rows were affected
      deleteResult must be(0)
    }
  }
}