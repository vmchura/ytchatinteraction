package controllers

import models.repository.{UserAvailabilityRepository, UserRepository}
import models.{User, UserTimezone}
import modules.DefaultEnv
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.db.evolutions.{Evolution, Evolutions, SimpleEvolutionsReader}
import play.api.db.slick.DatabaseConfigProvider
import play.api.db.{DBApi, Database}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{ActionBuilderImpl, AnyContent, Result}
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Injecting}
import play.api.test.CSRFTokenHelper.*
import play.silhouette.api.Silhouette
import play.silhouette.api.actions.SecuredActionBuilder
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class UserAvailabilityControllerIntegrationSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

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
  lazy val controllerComponents = app.injector.instanceOf[play.api.mvc.ControllerComponents]
  
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
          
          --- Add test data for UserAvailabilityControllerIntegrationSpec
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

  def createMockSecuredAction(user: User): SecuredActionBuilder[DefaultEnv, AnyContent] = {
    val mockSecuredAction = mock[SecuredActionBuilder[DefaultEnv, AnyContent]]
    val realActionBuilder = new ActionBuilderImpl(controllerComponents.parsers.default)(global)

    when(mockSecuredAction.async(any[play.silhouette.api.actions.SecuredRequest[DefaultEnv, AnyContent] => Future[Result]])).thenAnswer { invocation =>
      val func = invocation.getArgument[play.silhouette.api.actions.SecuredRequest[DefaultEnv, AnyContent] => Future[Result]](0)
      realActionBuilder.async { request =>
        val securedRequest = mock[play.silhouette.api.actions.SecuredRequest[DefaultEnv, AnyContent]]
        when(securedRequest.identity).thenReturn(user)
        when(securedRequest.session).thenReturn(request.session)
        when(securedRequest.cookies).thenReturn(request.cookies)
        when(securedRequest.method).thenReturn(request.method)
        when(securedRequest.body).thenReturn(request.body)
        when(securedRequest.headers).thenReturn(request.headers)
        when(securedRequest.attrs).thenReturn(request.attrs)
        when(securedRequest.connection).thenReturn(request.connection)
        when(securedRequest.flash).thenReturn(request.flash)
        when(securedRequest.target).thenReturn(request.target)
        when(securedRequest.uri).thenReturn(request.uri)
        when(securedRequest.path).thenReturn(request.path)
        when(securedRequest.version).thenReturn(request.version)
        when(securedRequest.queryString).thenReturn(request.queryString)
        when(securedRequest.acceptLanguages).thenReturn(Seq(play.api.i18n.Lang("en")))
        when(securedRequest.transientLang()).thenReturn(None)
        func(securedRequest)
      }
    }

    mockSecuredAction
  }

  def createControllerWithUser(user: User): (UserAvailabilityController, UserAvailabilityRepository, Silhouette[DefaultEnv]) = {
    val repository = new UserAvailabilityRepository(dbConfigProvider)
    val mockSilhouette = mock[Silhouette[DefaultEnv]]
    val mockSecuredAction = createMockSecuredAction(user)
    when(mockSilhouette.SecuredAction).thenReturn(mockSecuredAction)
    
    val controller = new UserAvailabilityController(
      controllerComponents,
      mockSilhouette,
      repository
    )
    
    (controller, repository, mockSilhouette)
  }

  "UserAvailabilityController#updateTimezone" should {
    "add new timezone for user without existing timezone" in {
      val testUser = User(testUserId, testUserName)
      val (controller, repository, _) = createControllerWithUser(testUser)
      
      // Verify no timezone exists initially
      val initialTimezone = Await.result(repository.getTimezone(testUserId), 5.seconds)
      initialTimezone must be(None)
      
      // Submit form to add timezone
      val request = FakeRequest("POST", "/profile/availability/timezone")
        .withFormUrlEncodedBody("timezone" -> testTimezone)
        .withCSRFToken
      
      val result = controller.updateTimezone()(request)
      
      // Should redirect with success message
      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some("/profile/availability"))
      flash(result).get("success") must be(Some("Timezone updated successfully"))
      
      // Verify timezone was saved
      val savedTimezone = Await.result(repository.getTimezone(testUserId), 5.seconds)
      savedTimezone must not be None
      savedTimezone.get.userId must be(testUserId)
      savedTimezone.get.timezone must be(testTimezone)
    }
    
    "modify existing timezone for user" in {
      val testUser = User(testUserId, testUserName)
      val (controller, repository, _) = createControllerWithUser(testUser)
      
      // First, add initial timezone
      val initialRequest = FakeRequest("POST", "/profile/availability/timezone")
        .withFormUrlEncodedBody("timezone" -> testTimezone)
        .withCSRFToken
      Await.result(controller.updateTimezone()(initialRequest), 5.seconds)
      
      // Verify initial timezone was saved
      val initialSaved = Await.result(repository.getTimezone(testUserId), 5.seconds)
      initialSaved must not be None
      initialSaved.get.timezone must be(testTimezone)
      
      // Now update the timezone
      val updateRequest = FakeRequest("POST", "/profile/availability/timezone")
        .withFormUrlEncodedBody("timezone" -> updatedTimezone)
        .withCSRFToken
      
      val result = controller.updateTimezone()(updateRequest)
      
      // Should redirect with success message
      status(result) must be(SEE_OTHER)
      redirectLocation(result) must be(Some("/profile/availability"))
      flash(result).get("success") must be(Some("Timezone updated successfully"))
      
      // Verify timezone was updated
      val updatedSaved = Await.result(repository.getTimezone(testUserId), 5.seconds)
      updatedSaved must not be None
      updatedSaved.get.userId must be(testUserId)
      updatedSaved.get.timezone must be(updatedTimezone)
    }
    
    "reject invalid timezone" in {
      val testUser = User(testUserId, testUserName)
      val (controller, repository, _) = createControllerWithUser(testUser)
      
      // Submit form with invalid timezone
      val request = FakeRequest("POST", "/profile/availability/timezone")
        .withFormUrlEncodedBody("timezone" -> "Invalid/Timezone")
        .withCSRFToken

      val result = controller.updateTimezone()(request)
      
      // Should return bad request (form validation error)
      status(result) must be(BAD_REQUEST)
    }
  }
}