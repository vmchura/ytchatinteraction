package models.repository

import models.{User, UserTimezone, UserAvailability, AvailabilityStatus}
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

class UserAvailabilityRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

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
  val testTimezone = "America/Lima"
  val updatedTimezone = "Europe/Madrid"

  override def beforeEach(): Unit = {
    super.beforeEach()
    
    // Apply the standard evolutions
    Evolutions.applyEvolutions(db)
    
    // Add test-specific data with an additional evolution
    Evolutions.applyEvolutions(db,
      SimpleEvolutionsReader.forDefault(
        Evolution(
          200,
          """
          --- !Ups
          
          --- Add test data for UserAvailabilityRepositorySpec
          INSERT INTO users (user_id, user_name) VALUES (1, 'Test User');
          """,
          """
          --- !Downs
          
          --- Remove test data (order matters due to FK constraints)
          DELETE FROM potential_matches WHERE first_user_availability_id IN (SELECT id FROM user_availability WHERE user_id = 1) OR second_user_availability_id IN (SELECT id FROM user_availability WHERE user_id = 1);
          DELETE FROM user_availability WHERE user_id = 1;
          DELETE FROM user_timezones WHERE user_id = 1;
          DELETE FROM users WHERE user_id = 1;
          """
        )
      )
    )
  }

  override def afterEach(): Unit = {
    // Note: We don't call Evolutions.cleanupEvolutions here because it causes issues
    // with foreign key constraints. The database is reset for each test suite via beforeEach.
    super.afterEach()
  }

  "UserAvailabilityRepository#updateTimezone" should {
    "insert new timezone when user has no existing timezone" in {
      val repository = new UserAvailabilityRepository(dbConfigProvider)
      
      // Verify no timezone exists initially
      val initialTimezoneF = repository.getTimezone(testUserId)
      val initialTimezone = Await.result(initialTimezoneF, 5.seconds)
      initialTimezone must be(None)
      
      // Create a new timezone
      val userTimezone = UserTimezone(testUserId, testTimezone, Instant.now(), Instant.now())
      val updateResultF = repository.updateTimezone(userTimezone)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify the insert worked (should affect 1 row)
      updateResult must be(1)
      
      // Verify the timezone was saved
      val savedTimezoneF = repository.getTimezone(testUserId)
      val savedTimezone = Await.result(savedTimezoneF, 5.seconds)
      
      savedTimezone must not be None
      savedTimezone.get.userId must be(testUserId)
      savedTimezone.get.timezone must be(testTimezone)
    }
    
    "update existing timezone when user already has one" in {
      val repository = new UserAvailabilityRepository(dbConfigProvider)
      
      // First, create an initial timezone
      val initialTimezone = UserTimezone(testUserId, testTimezone, Instant.now(), Instant.now())
      Await.result(repository.updateTimezone(initialTimezone), 5.seconds)
      
      // Verify initial timezone was saved
      val savedInitialF = repository.getTimezone(testUserId)
      val savedInitial = Await.result(savedInitialF, 5.seconds)
      savedInitial must not be None
      savedInitial.get.timezone must be(testTimezone)
      
      // Now update the timezone
      val updatedTimezoneObj = UserTimezone(testUserId, updatedTimezone, Instant.now(), Instant.now())
      val updateResultF = repository.updateTimezone(updatedTimezoneObj)
      val updateResult = Await.result(updateResultF, 5.seconds)
      
      // Verify the update worked (should affect 1 row)
      updateResult must be(1)
      
      // Verify the timezone was updated
      val savedUpdatedF = repository.getTimezone(testUserId)
      val savedUpdated = Await.result(savedUpdatedF, 5.seconds)
      
      savedUpdated must not be None
      savedUpdated.get.userId must be(testUserId)
      savedUpdated.get.timezone must be(updatedTimezone)
    }
    
    "handle multiple updates correctly" in {
      val repository = new UserAvailabilityRepository(dbConfigProvider)
      
      // First update
      val timezone1 = UserTimezone(testUserId, "America/New_York", Instant.now(), Instant.now())
      Await.result(repository.updateTimezone(timezone1), 5.seconds)
      
      // Second update
      val timezone2 = UserTimezone(testUserId, "America/Los_Angeles", Instant.now(), Instant.now())
      Await.result(repository.updateTimezone(timezone2), 5.seconds)
      
      // Third update
      val timezone3 = UserTimezone(testUserId, "Europe/London", Instant.now(), Instant.now())
      val finalResultF = repository.updateTimezone(timezone3)
      val finalResult = Await.result(finalResultF, 5.seconds)
      
      finalResult must be(1)
      
      // Verify final state
      val finalTimezoneF = repository.getTimezone(testUserId)
      val finalTimezone = Await.result(finalTimezoneF, 5.seconds)
      
      finalTimezone must not be None
      finalTimezone.get.timezone must be("Europe/London")
    }
  }
  
  "UserAvailabilityRepository#getTimezone" should {
    "return None when user has no timezone" in {
      val repository = new UserAvailabilityRepository(dbConfigProvider)
      
      // Query timezone for user without one
      val timezoneF = repository.getTimezone(testUserId)
      val timezone = Await.result(timezoneF, 5.seconds)
      
      timezone must be(None)
    }
    
    "return timezone when user has one" in {
      val repository = new UserAvailabilityRepository(dbConfigProvider)
      
      // Create a timezone first
      val userTimezone = UserTimezone(testUserId, testTimezone, Instant.now(), Instant.now())
      Await.result(repository.updateTimezone(userTimezone), 5.seconds)
      
      // Query the timezone
      val timezoneF = repository.getTimezone(testUserId)
      val timezone = Await.result(timezoneF, 5.seconds)
      
      timezone must not be None
      timezone.get.userId must be(testUserId)
      timezone.get.timezone must be(testTimezone)
    }
  }
  
  "UserAvailabilityRepository#deleteTimezone" should {
    "delete existing timezone" in {
      val repository = new UserAvailabilityRepository(dbConfigProvider)
      
      // Create a timezone
      val userTimezone = UserTimezone(testUserId, testTimezone, Instant.now(), Instant.now())
      Await.result(repository.updateTimezone(userTimezone), 5.seconds)
      
      // Verify it exists
      val beforeDeleteF = repository.getTimezone(testUserId)
      val beforeDelete = Await.result(beforeDeleteF, 5.seconds)
      beforeDelete must not be None
      
      // Delete it
      val deleteResultF = repository.deleteTimezone(testUserId)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      deleteResult must be(1)
      
      // Verify it's gone
      val afterDeleteF = repository.getTimezone(testUserId)
      val afterDelete = Await.result(afterDeleteF, 5.seconds)
      afterDelete must be(None)
    }
    
    "return 0 when deleting non-existent timezone" in {
      val repository = new UserAvailabilityRepository(dbConfigProvider)
      
      // Try to delete timezone that doesn't exist
      val deleteResultF = repository.deleteTimezone(testUserId)
      val deleteResult = Await.result(deleteResultF, 5.seconds)
      
      deleteResult must be(0)
    }
  }
}