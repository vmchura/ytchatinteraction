package models.repository

import models.repository.{LoginInfoRepository, UserRepository}
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
import play.silhouette.api.LoginInfo as SilhouetteLoginInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class LoginInfoRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

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
  val testUserId = 1L
  val testUserName = "Test User"
  val testProviderId = "test-provider"
  val testProviderKey = "test-provider-key"

  override def beforeEach(): Unit = {
    super.beforeEach()
    
    // Apply the standard evolutions
    Evolutions.applyEvolutions(db)
    
    // Then add test-specific data
    Evolutions.applyEvolutions(db,
      SimpleEvolutionsReader.forDefault(
        Evolution(
          20,
          """
          --- !Ups
          
          --- Add test data for LoginInfoRepositorySpec
          INSERT INTO users (user_id, user_name) VALUES (1, 'Test User');
          """,
          """
          --- !Downs
          
          --- Remove test data
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

  "LoginInfoRepository" should {
    "save and find LoginInfo" in {
      val repository = new LoginInfoRepository(dbConfigProvider, userRepository)
      
      // Create test data
      val loginInfo = models.LoginInfo(
        providerId = testProviderId,
        providerKey = testProviderKey,
        userId = testUserId
      )
      
      // Save the login info
      val resultF = repository.save(loginInfo)
      val result = Await.result(resultF, 5.seconds)
      
      // Verify the result
      result.id must not be None
      result.providerId must be(testProviderId)
      result.providerKey must be(testProviderKey)
      result.userId must be(testUserId)
      
      // Find the login info using Silhouette LoginInfo
      val silhouetteLoginInfo = SilhouetteLoginInfo(testProviderId, testProviderKey)
      val foundF = repository.find(silhouetteLoginInfo)
      val found = Await.result(foundF, 5.seconds)
      
      // Verify the found result
      found must not be None
      found.get.providerId must be(testProviderId)
      found.get.providerKey must be(testProviderKey)
      found.get.userId must be(testUserId)
    }
    
    "save should not create duplicate entries" in {
      val repository = new LoginInfoRepository(dbConfigProvider, userRepository)
      
      // Create test data
      val loginInfo = models.LoginInfo(
        providerId = testProviderId,
        providerKey = testProviderKey,
        userId = testUserId
      )
      
      // Save the login info
      val resultF = repository.save(loginInfo)
      val result = Await.result(resultF, 5.seconds)
      
      // Save again with the same provider ID and key
      val resultAgainF = repository.save(loginInfo)
      val resultAgain = Await.result(resultAgainF, 5.seconds)
      
      // Verify the results should have the same ID
      result.id must not be None
      resultAgain.id must be(result.id)
    }
    
    "find user by login info" in {
      val repository = new LoginInfoRepository(dbConfigProvider, userRepository)
      
      // Create test data
      val loginInfo = models.LoginInfo(
        providerId = testProviderId,
        providerKey = testProviderKey,
        userId = testUserId
      )
      
      // Save the login info
      Await.result(repository.save(loginInfo), 5.seconds)
      
      // Find user by Silhouette LoginInfo
      val silhouetteLoginInfo = SilhouetteLoginInfo(testProviderId, testProviderKey)
      val foundUserF = repository.findUser(silhouetteLoginInfo)
      val foundUser = Await.result(foundUserF, 5.seconds)
      
      // Verify the found user
      foundUser must not be None
      foundUser.get.userId must be(testUserId)
      foundUser.get.userName must be(testUserName)
    }
    
    "find all login info for a user" in {
      val repository = new LoginInfoRepository(dbConfigProvider, userRepository)
      
      // Create test data for multiple login infos for the same user
      val loginInfo1 = models.LoginInfo(
        providerId = testProviderId,
        providerKey = testProviderKey,
        userId = testUserId
      )
      
      val loginInfo2 = models.LoginInfo(
        providerId = "another-provider",
        providerKey = "another-key",
        userId = testUserId
      )
      
      // Save both login infos
      Await.result(repository.save(loginInfo1), 5.seconds)
      Await.result(repository.save(loginInfo2), 5.seconds)
      
      // Find all login infos for the user
      val foundInfosF = repository.findForUser(testUserId)
      val foundInfos = Await.result(foundInfosF, 5.seconds)
      
      // Verify results
      foundInfos.size must be(2)
      foundInfos.map(_.providerId) must contain allOf(testProviderId, "another-provider")
    }
    
    "add new login info for a user" in {
      val repository = new LoginInfoRepository(dbConfigProvider, userRepository)
      
      // Create Silhouette LoginInfo
      val silhouetteLoginInfo = SilhouetteLoginInfo(testProviderId, testProviderKey)
      
      // Add new login info
      val addedF = repository.add(testUserId, silhouetteLoginInfo)
      val added = Await.result(addedF, 5.seconds)
      
      // Verify the result
      added.id must not be None
      added.providerId must be(testProviderId)
      added.providerKey must be(testProviderKey)
      added.userId must be(testUserId)
      
      // Verify with find
      val foundF = repository.find(silhouetteLoginInfo)
      val found = Await.result(foundF, 5.seconds)
      
      found must not be None
      found.get.providerId must be(testProviderId)
    }
    
    "remove login info" in {
      val repository = new LoginInfoRepository(dbConfigProvider, userRepository)
      
      // Create test data
      val loginInfo = models.LoginInfo(
        providerId = testProviderId,
        providerKey = testProviderKey,
        userId = testUserId
      )
      
      // Save the login info
      Await.result(repository.save(loginInfo), 5.seconds)
      
      // Verify it exists
      val silhouetteLoginInfo = SilhouetteLoginInfo(testProviderId, testProviderKey)
      val beforeRemoveF = repository.find(silhouetteLoginInfo)
      val beforeRemove = Await.result(beforeRemoveF, 5.seconds)
      beforeRemove must not be None
      
      // Remove it
      Await.result(repository.remove(silhouetteLoginInfo), 5.seconds)
      
      // Verify it no longer exists
      val afterRemoveF = repository.find(silhouetteLoginInfo)
      val afterRemove = Await.result(afterRemoveF, 5.seconds)
      afterRemove must be(None)
    }
  }
}