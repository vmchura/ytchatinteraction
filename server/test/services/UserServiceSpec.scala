package services

import models.repository.{LoginInfoRepository, UserAliasRepository, UserRepository, YtUserRepository}
import models.User
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.RecoverMethods
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers.*
import play.silhouette.api.LoginInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*

class UserServiceSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach with ScalaFutures with RecoverMethods {

  // Mock repositories
  private val mockUserRepository = mock[UserRepository]
  private val mockLoginInfoRepository = mock[LoginInfoRepository]
  private val mockYtUserRepository = mock[YtUserRepository]
  private val mockUserAliasRepository = mock[UserAliasRepository]
  
  // Service under test
  private var userService: UserService = _
  
  // Test data
  private val testUserId = 1L
  private val testUserName = "Test User"
  private val testUser = User(testUserId, testUserName)
  private val providerId = "youtube"
  private val providerKey = "channel123"
  private val testLoginInfo = play.silhouette.api.LoginInfo(providerId, providerKey)
  
  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUserRepository, mockLoginInfoRepository, mockYtUserRepository, mockUserAliasRepository)
    userService = new UserServiceImpl(mockUserRepository, mockLoginInfoRepository, mockYtUserRepository, mockUserAliasRepository)
  }

  "UserService#retrieve" should {
    "delegate to LoginInfoRepository to find user by login info" in {
      // Arrange
      when(mockLoginInfoRepository.findUser(any[play.silhouette.api.LoginInfo]())).thenReturn(Future.successful(Some(testUser)))
      
      // Act
      val result = userService.retrieve(testLoginInfo).futureValue
      
      // Assert
      result must be(Some(testUser))
      verify(mockLoginInfoRepository).findUser(testLoginInfo)
    }
    
    "return None when login info does not exist" in {
      // Arrange
      when(mockLoginInfoRepository.findUser(any[play.silhouette.api.LoginInfo]())).thenReturn(Future.successful(None))
      
      // Act
      val result = userService.retrieve(testLoginInfo).futureValue
      
      // Assert
      result must be(None)
      verify(mockLoginInfoRepository).findUser(testLoginInfo)
    }
    
    "handle exceptions from repository gracefully" in {
      // Arrange
      when(mockLoginInfoRepository.findUser(any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.failed(new RuntimeException("Database error")))
      
      // Act & Assert
      recoverToExceptionIf[RuntimeException] {
        userService.retrieve(testLoginInfo)
      }.futureValue
    }
  }

  "UserService#save" should {
    "delegate to UserRepository to create a new user" in {
      // Arrange
      when(mockUserRepository.createWithAlias(any[String]())).thenReturn(Future.successful(testUser))
      
      // Act
      val result = userService.createUserWithAlias().futureValue
      
      // Assert
      result must be(testUser)
      verify(mockUserRepository).createWithAlias(any[String]())
    }
    
    "handle exceptions from repository gracefully" in {
      // Arrange
      when(mockUserRepository.createWithAlias(any[String]()))
        .thenReturn(Future.failed(new RuntimeException("Database error")))
      
      // Act & Assert
      recoverToExceptionIf[RuntimeException] {
        userService.createUserWithAlias()
      }.futureValue
    }
  }

  "UserService#link" should {
    "delegate to LoginInfoRepository to add a login info to user" in {
      // Arrange
      when(mockLoginInfoRepository.add(any[Long](), any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.successful(models.LoginInfo(Some(testUser.userId), providerId, providerKey, testUserId)))
      
      // Act
      val result = userService.link(testUser, testLoginInfo).futureValue
      
      // Assert
      result must be(testUser)
      verify(mockLoginInfoRepository).add(testUserId, testLoginInfo)
    }
    
    "handle exceptions from repository gracefully" in {
      // Arrange
      when(mockLoginInfoRepository.add(any[Long](), any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.failed(new RuntimeException("Database error")))
      
      // Act & Assert
      recoverToExceptionIf[RuntimeException] {
        userService.link(testUser, testLoginInfo)
      }.futureValue
    }
    
    "return the original user object after linking" in {
      // Arrange
      when(mockLoginInfoRepository.add(any[Long](), any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.successful(models.LoginInfo(Some(testUser.userId), providerId, providerKey, testUserId)))
      
      // Act
      val result = userService.link(testUser, testLoginInfo).futureValue
      
      // Assert
      result mustBe testUser
    }
  }

  "UserService#unlink" should {
    "delegate to LoginInfoRepository to remove a login info" in {
      // Arrange
      when(mockLoginInfoRepository.remove(any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.successful(()))
      
      // Act
      val result = userService.unlink(testUser, testLoginInfo).futureValue
      
      // Assert
      result must be(testUser)
      verify(mockLoginInfoRepository).remove(testLoginInfo)
    }
    
    "handle exceptions from repository gracefully" in {
      // Arrange
      when(mockLoginInfoRepository.remove(any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.failed(new RuntimeException("Database error")))
      
      // Act & Assert
      recoverToExceptionIf[RuntimeException] {
        userService.unlink(testUser, testLoginInfo)
      }.futureValue
    }
    
    "return the original user object after unlinking" in {
      // Arrange
      when(mockLoginInfoRepository.remove(any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.successful(()))
      
      // Act
      val result = userService.unlink(testUser, testLoginInfo).futureValue
      
      // Assert
      result must be(testUser)
    }
  }

  "UserService as IdentityService" should {
    "implement the silhouette IdentityService interface properly" in {
      // Arrange
      when(mockLoginInfoRepository.findUser(any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.successful(Some(testUser)))
      
      // Act
      val result = userService.retrieve(testLoginInfo).futureValue
      
      // Assert
      result must be(Some(testUser))
    }
  }
  
  "UserService with complex scenarios" should {
    "handle retrieving a user that doesn't exist" in {
      // Arrange
      when(mockLoginInfoRepository.findUser(any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.successful(None))
      
      // Act
      val result = userService.retrieve(testLoginInfo).futureValue
      
      // Assert
      result must be(None)
    }
    
    "handle linking a login info that already exists" in {
      // Arrange
      val existingLoginInfo = models.LoginInfo(Some(1L), providerId, providerKey, testUserId)
      
      when(mockLoginInfoRepository.add(any[Long](), any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.successful(existingLoginInfo))
      
      // Act
      val result = userService.link(testUser, testLoginInfo).futureValue
      
      // Assert
      result must be(testUser)
      verify(mockLoginInfoRepository).add(testUserId, testLoginInfo)
    }
    
    "handle unlinking a login info that doesn't exist" in {
      // Arrange
      when(mockLoginInfoRepository.remove(any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.successful(()))
      
      // Act
      val result = userService.unlink(testUser, testLoginInfo).futureValue
      
      // Assert
      result must be(testUser)
      verify(mockLoginInfoRepository).remove(testLoginInfo)
    }
  }
  
  "UserService implementation" should {
    "not interact with YtUserRepository directly" in {
      // This test verifies that UserServiceImpl doesn't call YtUserRepository directly
      // for any of its core operations (it's injected but not used directly)
      
      // Arrange
      when(mockLoginInfoRepository.findUser(any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.successful(Some(testUser)))
      when(mockUserRepository.createWithAlias(any[String]()))
        .thenReturn(Future.successful(testUser))
      when(mockLoginInfoRepository.add(any[Long](), any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.successful(models.LoginInfo(Some(testUser.userId), providerId, providerKey, testUserId)))
      when(mockLoginInfoRepository.remove(any[play.silhouette.api.LoginInfo]()))
        .thenReturn(Future.successful(()))
      
      // Act
      userService.retrieve(testLoginInfo).futureValue
      userService.createUserWithAlias().futureValue
      userService.link(testUser, testLoginInfo).futureValue
      userService.unlink(testUser, testLoginInfo).futureValue
      
      // Assert - verify that YtUserRepository was never called
      verifyNoInteractions(mockYtUserRepository)
    }
  }
}