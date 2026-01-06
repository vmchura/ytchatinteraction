package models.repository

import models.YtUser
import models.repository.{OAuth2InfoRepository, YtUserRepository}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.db.evolutions.*
import play.api.db.slick.DatabaseConfigProvider
import play.api.db.{DBApi, Database}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.*
import play.api.test.Injecting
import play.db.evolutions.EvolutionsReader
import play.silhouette.api.LoginInfo
import play.silhouette.impl.providers.OAuth2Info
import slick.jdbc.{H2Profile, JdbcProfile}

import java.io.File
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class OAuth2InfoRepositorySpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

  // We'll use an in-memory H2 database for testing
  override def fakeApplication(): Application = {
    // You can customize the application here as needed
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

  // Lazy vals for components we need throughout the test
  lazy val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
  lazy val ytUserRepository = mock[YtUserRepository]
  lazy val db = app.injector.instanceOf[DBApi].database("default")
  
  // Setup test data for YtUserRepository mock
  val testChannelId = "UCTestChannelId"
  val testUserId = 1L
  val ytUser = YtUser(
    userChannelId = testChannelId,
    userId = testUserId,
    displayName = Some("Test User"),
    email = Some("test@example.com"),
    profileImageUrl = Some("http://example.com/image.jpg"),
    activated = true,
    createdAt = java.time.Instant.now(),
    updatedAt = java.time.Instant.now()
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    
    // First apply the standard evolutions from the application
    // This will use the 1.sql file from conf/evolutions/default
    Evolutions.applyEvolutions(db)
    
    // Then add test-specific data with an additional evolution
    Evolutions.applyEvolutions(db,
      SimpleEvolutionsReader.forDefault(
        Evolution(
          200,
          """
          --- !Ups
          
          --- Add test data for OAuth2InfoRepositorySpec
          INSERT INTO users (user_id, user_name) VALUES (1, 'Test User');
          INSERT INTO yt_users (user_channel_id, user_id, display_name, email, activated) 
          VALUES ('UCTestChannelId', 1, 'Test User', 'test@example.com', TRUE);
          """,
          """
          --- !Downs
          
          --- Remove test data
          DELETE FROM yt_users WHERE user_channel_id = 'UCTestChannelId';
          DELETE FROM users WHERE user_id = 1;
          DELETE FROM oauth2_tokens where 1 = 1;
          """
        )
      )
    )
    
    // Set up the mock responses
    when(ytUserRepository.getByChannelId(testChannelId)).thenReturn(Future.successful(Some(ytUser)))
  }

  override def afterEach(): Unit = {
    // Clean up the database after each test
    Evolutions.cleanupEvolutions(db)
    
    super.afterEach()
  }

  "OAuth2InfoRepository" should {
    "add and find OAuth2Info" in {
      val repository = new OAuth2InfoRepository(dbConfigProvider, ytUserRepository)
      
      // Create test data
      val loginInfo = LoginInfo("google", testChannelId)
      val oAuth2Info = new OAuth2Info(
        accessToken = "test-access-token",
        tokenType = Some("Bearer"),
        expiresIn = Some(3600),
        refreshToken = Some("test-refresh-token")
      )
      
      // Add the OAuth2Info
      val resultF = repository.add(loginInfo, oAuth2Info)
      val result = Await.result(resultF, 5.seconds)
      
      // Verify the result
      result.accessToken must be("test-access-token")
      result.tokenType must be(Some("Bearer"))
      result.expiresIn must be(Some(3600))
      result.refreshToken must be(Some("test-refresh-token"))
      
      // Find the OAuth2Info
      val foundF = repository.find(loginInfo)
      val found = Await.result(foundF, 5.seconds)
      
      // Verify the found result
      found must not be None
      found.get.accessToken must be("test-access-token")
    }
    
    "update existing OAuth2Info" in {
      val repository = new OAuth2InfoRepository(dbConfigProvider, ytUserRepository)
      
      // Create test data
      val loginInfo = LoginInfo("google", testChannelId)
      val oAuth2Info1 = new OAuth2Info(
        accessToken = "initial-access-token",
        tokenType = Some("Bearer"),
        expiresIn = Some(3600),
        refreshToken = Some("initial-refresh-token")
      )
      
      val oAuth2Info2 = new OAuth2Info(
        accessToken = "updated-access-token",
        tokenType = Some("Bearer"),
        expiresIn = Some(7200),
        refreshToken = Some("updated-refresh-token")
      )
      
      // Add the initial OAuth2Info
      Await.result(repository.add(loginInfo, oAuth2Info1), 5.seconds)
      
      // Update with new OAuth2Info
      val updatedF = repository.update(loginInfo, oAuth2Info2)
      val updated = Await.result(updatedF, 5.seconds)
      
      // Verify the updated result
      updated.accessToken must be("updated-access-token")
      updated.expiresIn must be(Some(7200))
      
      // Find to verify the update
      val foundF = repository.find(loginInfo)
      val found = Await.result(foundF, 5.seconds)
      
      found must not be None
      found.get.accessToken must be("updated-access-token")
      found.get.refreshToken must be(Some("updated-refresh-token"))
    }
    
    "save OAuth2Info (create or update)" in {
      val repository = new OAuth2InfoRepository(dbConfigProvider, ytUserRepository)
      
      // Create test data
      val loginInfo = LoginInfo("google", testChannelId)
      val oAuth2Info = new OAuth2Info(
        accessToken = "save-test-token",
        tokenType = Some("Bearer"),
        expiresIn = Some(3600),
        refreshToken = Some("save-test-refresh")
      )
      
      // Save (should create)
      val savedF = repository.save(loginInfo, oAuth2Info)
      val saved = Await.result(savedF, 5.seconds)
      
      saved.accessToken must be("save-test-token")
      
      // Save again (should update)
      val updated = oAuth2Info.copy(accessToken = "save-updated-token")
      val updatedF = repository.save(loginInfo, updated)
      val result = Await.result(updatedF, 5.seconds)
      
      result.accessToken must be("save-updated-token")
      
      // Verify with find
      val foundF = repository.find(loginInfo)
      val found = Await.result(foundF, 5.seconds)
      
      found must not be None
      found.get.accessToken must be("save-updated-token")
    }
    
    "remove OAuth2Info" in {
      val repository = new OAuth2InfoRepository(dbConfigProvider, ytUserRepository)
      
      // Create test data
      val loginInfo = LoginInfo("google", testChannelId)
      val oAuth2Info = new OAuth2Info(
        accessToken = "to-be-removed",
        tokenType = Some("Bearer"),
        expiresIn = Some(3600),
        refreshToken = Some("to-be-removed-refresh")
      )
      
      // Add OAuth2Info
      Await.result(repository.add(loginInfo, oAuth2Info), 5.seconds)
      
      // Verify it exists
      val beforeRemoveF = repository.find(loginInfo)
      val beforeRemove = Await.result(beforeRemoveF, 5.seconds)
      beforeRemove must not be None
      
      // Remove it
      Await.result(repository.remove(loginInfo), 5.seconds)
      
      // Verify it no longer exists
      val afterRemoveF = repository.find(loginInfo)
      val afterRemove = Await.result(afterRemoveF, 5.seconds)
      afterRemove must be(None)
    }
  }
}