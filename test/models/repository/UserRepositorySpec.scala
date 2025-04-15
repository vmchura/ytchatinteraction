package models.repository

import models.User
import models.repository.UserRepository
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

class UserRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

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
  
  // Test data
  val testUserId = 1L
  val testUserName = "Test User"
  val updatedUserName = "Updated User"
  val testUserName2 = "Another User"

  override def beforeEach(): Unit = {
    super.beforeEach()
    
    // Apply the standard evolutions
    Evolutions.applyEvolutions(db)
    
    // No need for test-specific data in beforeEach for UserRepository tests
    // as we'll create users in each test
  }

  override def afterEach(): Unit = {
    // Clean up the database after each test
    Evolutions.cleanupEvolutions(db)
    
    super.afterEach()
  }

  "UserRepository" should {
    "create a new user" in {
      val repository = new UserRepository(dbConfigProvider)
      
      // Create a new user
      val createdF = repository.create(testUserName)
      val created = Await.result(createdF, 5.seconds)
      
      // Verify the result
      created.userId must be > 0L
      created.userName must be(testUserName)
    }
    
    "list all users" in {
      val repository = new UserRepository(dbConfigProvider)
      
      // Create multiple users
      val user1 = Await.result(repository.create(testUserName), 5.seconds)
      val user2 = Await.result(repository.create(testUserName2), 5.seconds)
      
      // List all users
      val usersF = repository.list()
      val users = Await.result(usersF, 5.seconds)
      
      // Verify the results
      users.size must be(2)
      users.map(_.userName) must contain allOf(testUserName, testUserName2)
    }
    
    "get user by ID" in {
      val repository = new UserRepository(dbConfigProvider)
      
      // Create a user
      val created = Await.result(repository.create(testUserName), 5.seconds)
      
      // Get user by ID
      val foundF = repository.getById(created.userId)
      val found = Await.result(foundF, 5.seconds)
      
      // Verify the result
      found must not be None
      found.get.userId must be(created.userId)
      found.get.userName must be(testUserName)
    }
    
    "get user by username" in {
      val repository = new UserRepository(dbConfigProvider)
      
      // Create a user
      Await.result(repository.create(testUserName), 5.seconds)
      
      // Get user by username
      val foundF = repository.getByUsername(testUserName)
      val found = Await.result(foundF, 5.seconds)
      
      // Verify the result
      found must not be None
      found.get.userName must be(testUserName)
    }
    
    "update a user" in {
      val repository = new UserRepository(dbConfigProvider)
      
      // Create a user
      val created = Await.result(repository.create(testUserName), 5.seconds)
      
      // Update the user
      val updatedUser = User(created.userId, updatedUserName)
      val updateResultF = repository.update(updatedUser)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify the update worked
      updateResult must be(1) // 1 row affected
      
      // Get the user to confirm the update
      val foundF = repository.getById(created.userId)
      val found = Await.result(foundF, 5.seconds)
      
      found must not be None
      found.get.userName must be(updatedUserName)
    }
    
    "check if a user exists" in {
      val repository = new UserRepository(dbConfigProvider)
      
      // Create a user
      val created = Await.result(repository.create(testUserName), 5.seconds)
      
      // Check if user exists
      val existsF = repository.exists(created.userId)
      val exists = Await.result(existsF, 5.seconds)
      
      // Verify the user exists
      exists must be(true)
      
      // Check for non-existent user
      val nonExistentF = repository.exists(9999L)
      val nonExistent = Await.result(nonExistentF, 5.seconds)
      
      // Verify the non-existent user doesn't exist
      nonExistent must be(false)
    }
    
    "delete a user" in {
      val repository = new UserRepository(dbConfigProvider)
      
      // Create a user
      val created = Await.result(repository.create(testUserName), 5.seconds)
      
      // Verify the user exists before deletion
      val existsBeforeF = repository.exists(created.userId)
      val existsBefore = Await.result(existsBeforeF, 5.seconds)
      existsBefore must be(true)
      
      // Delete the user
      val deleteResultF = repository.delete(created.userId)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify the delete worked
      deleteResult must be(1) // 1 row affected
      
      // Check that the user no longer exists
      val existsAfterF = repository.exists(created.userId)
      val existsAfter = Await.result(existsAfterF, 5.seconds)
      existsAfter must be(false)
    }
    
    "return None when getting a non-existent user" in {
      val repository = new UserRepository(dbConfigProvider)
      
      // Get non-existent user by ID
      val foundByIdF = repository.getById(9999L)
      val foundById = Await.result(foundByIdF, 5.seconds)
      
      // Verify the result
      foundById must be(None)
      
      // Get non-existent user by username
      val foundByUsernameF = repository.getByUsername("Non-existent User")
      val foundByUsername = Await.result(foundByUsernameF, 5.seconds)
      
      // Verify the result
      foundByUsername must be(None)
    }
    
    "handle update for non-existent user" in {
      val repository = new UserRepository(dbConfigProvider)
      
      // Update a non-existent user
      val nonExistentUser = User(9999L, "Non-existent User")
      val updateResultF = repository.update(nonExistentUser)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify no rows were affected
      updateResult must be(0)
    }
    
    "handle delete for non-existent user" in {
      val repository = new UserRepository(dbConfigProvider)
      
      // Delete a non-existent user
      val deleteResultF = repository.delete(9999L)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      // Verify no rows were affected
      deleteResult must be(0)
    }
  }
}