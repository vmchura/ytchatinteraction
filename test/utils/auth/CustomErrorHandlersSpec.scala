package utils.auth

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.test._
import play.api.test.Helpers._
import play.api.mvc.{RequestHeader, Results}
import play.api.i18n.MessagesApi
import controllers.routes

import scala.concurrent.ExecutionContext.Implicits.global

class CustomErrorHandlersSpec extends PlaySpec with MockitoSugar {

  "CustomSecuredErrorHandler" should {
    
    "redirect to index with error flash message when user is not authenticated" in {
      val messagesApi = mock[MessagesApi]
      val errorHandler = new CustomSecuredErrorHandler()(messagesApi)
      
      implicit val request: RequestHeader = FakeRequest("GET", "/admin")
      
      val result = errorHandler.onNotAuthenticated
      
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("/")
      flash(result).get("error") mustEqual Some("You must be logged in to access this page.")
    }
    
    "redirect to index with authorization error flash message when user is not authorized" in {
      val messagesApi = mock[MessagesApi]
      val errorHandler = new CustomSecuredErrorHandler()(messagesApi)
      
      implicit val request: RequestHeader = FakeRequest("GET", "/admin")
      
      val result = errorHandler.onNotAuthorized
      
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("/")
      flash(result).get("error") mustEqual Some("You don't have permission to access this page.")
    }
  }
  
  "CustomUnsecuredErrorHandler" should {
    
    "redirect to index when authenticated user tries to access unsecured-only endpoint" in {
      val messagesApi = mock[MessagesApi]
      val errorHandler = new CustomUnsecuredErrorHandler()(messagesApi)
      
      implicit val request: RequestHeader = FakeRequest("GET", "/login")
      
      val result = errorHandler.onNotAuthorized
      
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("/")
    }
  }
}
