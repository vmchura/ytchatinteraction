package models.repository

import models.YtUser
import models.repository.{UserRepository, YtUserRepository}
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

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class YtUserRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

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
  val testEmail1 = "user1@example.com"
  val testEmail2 = "user2@example.com"
  val testDisplayName1 = "Display Name 1"
  val testDisplayName2 = "Display Name 2"
  val testProfileImageUrl1 = "https://example.com/image1.jpg"
  val testProfileImageUrl2 = "https://example.com/image2.jpg"

  override def beforeEach(): Unit = {
    super.beforeEach()
    
    // Apply the standard evolutions
    Evolutions.applyEvolutions(db)
    
    // Add test-specific data - only users, we'll create YT users in tests
    Evolutions.applyEvolutions(db,
      SimpleEvolutionsReader.forDefault(
        Evolution(
          200,
          """
          --- !Ups
          
          --- Add test data for YtUserRepositorySpec
          INSERT INTO users (user_id, user_name) VALUES (1, 'Test User 1');
          INSERT INTO users (user_id, user_name) VALUES (2, 'Test User 2');
          """,
          """
          --- !Downs
          
          --- Remove test data
          DELETE FROM yt_users WHERE user_id IN (1,2);
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

  "YtUserRepository" should {
    
    
    "create a YouTube user with full information" in {
      val repository = new YtUserRepository(dbConfigProvider, userRepository)
      
      // Create a full YouTube user
      val ytUser = YtUser(
        userChannelId = testChannelId1,
        userId = testUserId1,
        displayName = Some(testDisplayName1),
        email = Some(testEmail1),
        profileImageUrl = Some(testProfileImageUrl1),
        activated = true
      )
      
      val createdF = repository.createFull(ytUser)
      val created = Await.result(createdF, 5.seconds)
      
      // Verify the result
      created.userChannelId must be(testChannelId1)
      created.userId must be(testUserId1)
      created.displayName must be(Some(testDisplayName1))
      created.email must be(Some(testEmail1))
      created.profileImageUrl must be(Some(testProfileImageUrl1))
      created.activated must be(true)
      created.createdAt must not be null
      created.updatedAt must not be null
    }
    
    "list all YouTube users" in {
      val repository = new YtUserRepository(dbConfigProvider, userRepository)
      
      // Create multiple YouTube users
      val ytUser1 = YtUser(
        userChannelId = testChannelId1,
        userId = testUserId1,
        displayName = Some(testDisplayName1),
        email = Some(testEmail1)
      )
      
      val ytUser2 = YtUser(
        userChannelId = testChannelId2,
        userId = testUserId2,
        displayName = Some(testDisplayName2),
        email = Some(testEmail2)
      )
      
      Await.result(repository.createFull(ytUser1), 5.seconds)
      Await.result(repository.createFull(ytUser2), 5.seconds)
      
      // List all YouTube users
      val usersF = repository.list()
      val users = Await.result(usersF, 5.seconds)
      
      // Verify the results
      users.size must be(2)
      users.map(_.userChannelId) must contain allOf(testChannelId1, testChannelId2)
      users.find(_.userChannelId == testChannelId1).get.displayName must be(Some(testDisplayName1))
      users.find(_.userChannelId == testChannelId2).get.displayName must be(Some(testDisplayName2))
    }
    
    "get YouTube user by channel ID" in {
      val repository = new YtUserRepository(dbConfigProvider, userRepository)
      
      // Create a YouTube user
      val ytUser = YtUser(
        userChannelId = testChannelId1,
        userId = testUserId1,
        displayName = Some(testDisplayName1),
        email = Some(testEmail1)
      )
      
      Await.result(repository.createFull(ytUser), 5.seconds)
      
      // Get user by channel ID
      val userF = repository.getByChannelId(testChannelId1)
      val user = Await.result(userF, 5.seconds)
      
      // Verify the result
      user must not be None
      user.get.userChannelId must be(testChannelId1)
      user.get.userId must be(testUserId1)
      user.get.displayName must be(Some(testDisplayName1))
      user.get.email must be(Some(testEmail1))
    }
    
    "return None when getting a non-existent YouTube user" in {
      val repository = new YtUserRepository(dbConfigProvider, userRepository)
      
      // Get non-existent user by channel ID
      val userF = repository.getByChannelId("NonExistentChannelId")
      val user = Await.result(userF, 5.seconds)
      
      // Verify the result
      user must be(None)
    }
    
    "get YouTube users by user ID" in {
      val repository = new YtUserRepository(dbConfigProvider, userRepository)
      
      // Create multiple YouTube users for the same user
      val ytUser1 = YtUser(
        userChannelId = testChannelId1,
        userId = testUserId1,
        displayName = Some(testDisplayName1)
      )
      
      val ytUser2 = YtUser(
        userChannelId = testChannelId2,
        userId = testUserId1,
        displayName = Some(testDisplayName2)
      )
      
      Await.result(repository.createFull(ytUser1), 5.seconds)
      Await.result(repository.createFull(ytUser2), 5.seconds)
      
      // Get users by user ID
      val usersF = repository.getByUserId(testUserId1)
      val users = Await.result(usersF, 5.seconds)
      
      // Verify the results
      users.size must be(2)
      users.map(_.userChannelId) must contain allOf(testChannelId1, testChannelId2)
      users.foreach(_.userId must be(testUserId1))
    }
    
    "update a YouTube user" in {
      val repository = new YtUserRepository(dbConfigProvider, userRepository)
      
      // Create a YouTube user
      val ytUser = YtUser(
        userChannelId = testChannelId1,
        userId = testUserId1,
        displayName = Some(testDisplayName1),
        email = Some(testEmail1)
      )
      
      Await.result(repository.createFull(ytUser), 5.seconds)
      
      // Update the user
      val updatedUser = ytUser.copy(
        displayName = Some(testDisplayName2),
        email = Some(testEmail2),
        profileImageUrl = Some(testProfileImageUrl1),
        activated = true
      )
      
      val updateResultF = repository.update(updatedUser)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify the update worked
      updateResult must be(1) // 1 row affected
      
      // Get the user to confirm the update
      val userF = repository.getByChannelId(testChannelId1)
      val user = Await.result(userF, 5.seconds)
      
      user must not be None
      user.get.displayName must be(Some(testDisplayName2))
      user.get.email must be(Some(testEmail2))
      user.get.profileImageUrl must be(Some(testProfileImageUrl1))
      user.get.activated must be(true)
      // Make sure updatedAt is newer than original updatedAt
      user.get.updatedAt.isAfter(ytUser.updatedAt) must be(true)
    }
    
    "activate or deactivate a YouTube account" in {
      val repository = new YtUserRepository(dbConfigProvider, userRepository)
      
      // Create a deactivated YouTube user
      val ytUser = YtUser(
        userChannelId = testChannelId1,
        userId = testUserId1,
        activated = false
      )
      
      Await.result(repository.createFull(ytUser), 5.seconds)
      
      // Activate the account
      val activateResultF = repository.setActivated(testChannelId1, true)
      val activateResult = Await.result(activateResultF, 5.seconds)
      
      // Verify the activation worked
      activateResult must be(1) // 1 row affected
      
      // Get the user to confirm the activation
      val userF = repository.getByChannelId(testChannelId1)
      val user = Await.result(userF, 5.seconds)
      
      user must not be None
      user.get.activated must be(true)
      
      // Deactivate the account
      val deactivateResultF = repository.setActivated(testChannelId1, false)
      val deactivateResult = Await.result(deactivateResultF, 5.seconds)
      
      // Verify the deactivation worked
      deactivateResult must be(1) // 1 row affected
      
      // Get the user to confirm the deactivation
      val userAfterF = repository.getByChannelId(testChannelId1)
      val userAfter = Await.result(userAfterF, 5.seconds)
      
      userAfter must not be None
      userAfter.get.activated must be(false)
    }
    
    
    "delete a YouTube user" in {
      val repository = new YtUserRepository(dbConfigProvider, userRepository)
      
      // Create a YouTube user
      val ytUser = YtUser(
        userChannelId = testChannelId1,
        userId = testUserId1
      )
      
      Await.result(repository.createFull(ytUser), 5.seconds)
      
      // Verify the user exists before deletion
      val existsBeforeF = repository.getByChannelId(testChannelId1)
      val existsBefore = Await.result(existsBeforeF, 5.seconds)
      existsBefore must not be None
      
      // Delete the user
      val deleteResultF = repository.delete(testChannelId1)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify the delete worked
      deleteResult must be(1) // 1 row affected
      
      // Check that the user no longer exists
      val existsAfterF = repository.getByChannelId(testChannelId1)
      val existsAfter = Await.result(existsAfterF, 5.seconds)
      existsAfter must be(None)
    }
    
    "handle update for non-existent YouTube user" in {
      val repository = new YtUserRepository(dbConfigProvider, userRepository)
      
      // Update a non-existent user
      val nonExistentUser = YtUser(
        userChannelId = "NonExistentChannelId",
        userId = testUserId1,
        displayName = Some(testDisplayName1)
      )
      
      val updateResultF = repository.update(nonExistentUser)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify no rows were affected
      updateResult must be(0)
    }
    
    "handle setActivated for non-existent YouTube user" in {
      val repository = new YtUserRepository(dbConfigProvider, userRepository)
      
      // Activate a non-existent user
      val setActivatedF = repository.setActivated("NonExistentChannelId", true)
      val setActivated = Await.result(setActivatedF, 5.seconds)
      
      // Verify no rows were affected
      setActivated must be(0)
    }
    
    "handle delete for non-existent YouTube user" in {
      val repository = new YtUserRepository(dbConfigProvider, userRepository)
      
      // Delete a non-existent user
      val deleteResultF = repository.delete("NonExistentChannelId")
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify no rows were affected
      deleteResult must be(0)
    }
  }
}
