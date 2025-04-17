package services

import models.repository.{EventPollRepository, PollOptionRepository, PollVoteRepository, StreamerEventRepository, UserStreamerStateRepository}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.RecoverMethods
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.{DBIO, DBIOAction}
import slick.jdbc.JdbcProfile
import slick.jdbc.H2Profile
import slick.jdbc.H2Profile.api._
import javax.inject.Singleton

class PollServiceSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach with ScalaFutures with RecoverMethods {

  // Mock repositories
  private val mockStreamerEventRepository = mock[StreamerEventRepository]
  private val mockEventPollRepository = mock[EventPollRepository]
  private val mockPollOptionRepository = mock[PollOptionRepository]
  private val mockPollVoteRepository = mock[PollVoteRepository]
  private val mockUserStreamerStateRepository = mock[UserStreamerStateRepository]
  private val mockDbConfigProvider = mock[DatabaseConfigProvider]
  
  // Service under test
  private var pollService: PollService = _
  
  // Create a mock database for testing
  private val db = Database.forURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
  
  // Test data
  private val testEventId = 1
  private val testUserId = 2L
  private val testStreamChannelId = "test-channel-123"
  
  trait MockDbConfig {
    val profile = H2Profile
    val db = Database.forURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
  }
  
  object MockDbConfig extends MockDbConfig
  
  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockStreamerEventRepository, 
      mockEventPollRepository, 
      mockPollOptionRepository, 
      mockPollVoteRepository,
      mockUserStreamerStateRepository,
      mockDbConfigProvider
    )
    
    // Set up the mock repository with the mock database

    pollService = new PollService(
      mockStreamerEventRepository,
      mockEventPollRepository,
      mockPollOptionRepository,
      mockPollVoteRepository,
      mockUserStreamerStateRepository
    )
  }

  /**
   * Helper method to execute DBIO actions in tests
   */
  def execDBIO[T](action: DBIO[T]): T = {
    Await.result(db.run(action), 5.seconds)
  }

  "PollService#transferConfidenceVoteStreamer" should {
    "transfer confidence amount successfully when all conditions are met" in {
      // Arrange
      val eventBalance = 100
      val userBalance = 50
      val transferAmount = 20
      
      when(mockStreamerEventRepository.getCurrentConfidenceAmountAction(testEventId))
        .thenReturn(DBIO.successful(Some(eventBalance)))
      
      when(mockUserStreamerStateRepository.getUserStreamerBalanceAction(testUserId, testStreamChannelId))
        .thenReturn(DBIO.successful(Some(userBalance)))
      
      when(mockStreamerEventRepository.updateCurrentConfidenceAmount(testEventId, eventBalance + transferAmount))
        .thenReturn(DBIO.successful(1))
      
      when(mockUserStreamerStateRepository.updateStreamerBalanceAction(testUserId, testStreamChannelId, userBalance - transferAmount))
        .thenReturn(DBIO.successful(1))
      
      // Act & Assert
      val dbioAction = pollService.transferConfidenceVoteStreamer(testEventId, testUserId, testStreamChannelId, transferAmount)
      val result = execDBIO(dbioAction)
      result mustBe true
      
      verify(mockStreamerEventRepository).getCurrentConfidenceAmountAction(testEventId)
      verify(mockUserStreamerStateRepository).getUserStreamerBalanceAction(testUserId, testStreamChannelId)
      verify(mockStreamerEventRepository).updateCurrentConfidenceAmount(testEventId, eventBalance + transferAmount)
      verify(mockUserStreamerStateRepository).updateStreamerBalanceAction(testUserId, testStreamChannelId, userBalance - transferAmount)
    }
    
    "transfer all user balance when Int.MaxValue is provided as amount" in {
      // Arrange
      val eventBalance = 100
      val userBalance = 50
      val transferAmount = Int.MaxValue // Special value to transfer all balance
      
      when(mockStreamerEventRepository.getCurrentConfidenceAmountAction(testEventId))
        .thenReturn(DBIO.successful(Some(eventBalance)))
      
      when(mockUserStreamerStateRepository.getUserStreamerBalanceAction(testUserId, testStreamChannelId))
        .thenReturn(DBIO.successful(Some(userBalance)))
      
      when(mockStreamerEventRepository.updateCurrentConfidenceAmount(testEventId, eventBalance + userBalance))
        .thenReturn(DBIO.successful(1))
      
      when(mockUserStreamerStateRepository.updateStreamerBalanceAction(testUserId, testStreamChannelId, 0))
        .thenReturn(DBIO.successful(1))
      
      // Act & Assert
      val dbioAction = pollService.transferConfidenceVoteStreamer(testEventId, testUserId, testStreamChannelId, transferAmount)
      val result = execDBIO(dbioAction)
      result mustBe true
      
      verify(mockStreamerEventRepository).getCurrentConfidenceAmountAction(testEventId)
      verify(mockUserStreamerStateRepository).getUserStreamerBalanceAction(testUserId, testStreamChannelId)
      verify(mockStreamerEventRepository).updateCurrentConfidenceAmount(testEventId, eventBalance + userBalance)
      verify(mockUserStreamerStateRepository).updateStreamerBalanceAction(testUserId, testStreamChannelId, 0)
    }
    
    "fail when event balance is not found" in {
      // Arrange
      when(mockStreamerEventRepository.getCurrentConfidenceAmountAction(testEventId))
        .thenReturn(DBIO.successful(None))
      
      // Act & Assert
      val dbioAction = pollService.transferConfidenceVoteStreamer(testEventId, testUserId, testStreamChannelId, 10)
      
      val thrown = intercept[IllegalStateException] {
        execDBIO(dbioAction)
      }
      thrown.getMessage must include("No balance of the event found")
      
      verify(mockStreamerEventRepository).getCurrentConfidenceAmountAction(testEventId)
      verifyNoMoreInteractions(mockUserStreamerStateRepository)
    }
    
    "fail when user balance is not found" in {
      // Arrange
      when(mockStreamerEventRepository.getCurrentConfidenceAmountAction(testEventId))
        .thenReturn(DBIO.successful(Some(100)))
        
      when(mockUserStreamerStateRepository.getUserStreamerBalanceAction(testUserId, testStreamChannelId))
        .thenReturn(DBIO.successful(None))
      
      // Act & Assert
      val dbioAction = pollService.transferConfidenceVoteStreamer(testEventId, testUserId, testStreamChannelId, 10)
      
      val thrown = intercept[IllegalStateException] {
        execDBIO(dbioAction)
      }
      thrown.getMessage must include("No balance of the user channel found")
      
      verify(mockStreamerEventRepository).getCurrentConfidenceAmountAction(testEventId)
      verify(mockUserStreamerStateRepository).getUserStreamerBalanceAction(testUserId, testStreamChannelId)
    }
    
    "fail when transfer would result in negative event balance" in {
      // Arrange
      val eventBalance = -10 // Already negative
      val userBalance = 50
      val transferAmount = -20 // Trying to decrease further
      
      when(mockStreamerEventRepository.getCurrentConfidenceAmountAction(testEventId))
        .thenReturn(DBIO.successful(Some(eventBalance)))
      
      when(mockUserStreamerStateRepository.getUserStreamerBalanceAction(testUserId, testStreamChannelId))
        .thenReturn(DBIO.successful(Some(userBalance)))
      
      // Act & Assert
      val dbioAction = pollService.transferConfidenceVoteStreamer(testEventId, testUserId, testStreamChannelId, transferAmount)
      
      val thrown = intercept[IllegalStateException] {
        execDBIO(dbioAction)
      }
      thrown.getMessage must include("Negative balance for streamer event")
      
      verify(mockStreamerEventRepository).getCurrentConfidenceAmountAction(testEventId)
      verify(mockUserStreamerStateRepository).getUserStreamerBalanceAction(testUserId, testStreamChannelId)
      verifyNoMoreInteractions(mockStreamerEventRepository)
    }
    
    "fail when transfer would result in negative user balance" in {
      // Arrange
      val eventBalance = 100
      val userBalance = 10
      val transferAmount = 20 // More than user balance
      
      when(mockStreamerEventRepository.getCurrentConfidenceAmountAction(testEventId))
        .thenReturn(DBIO.successful(Some(eventBalance)))
      
      when(mockUserStreamerStateRepository.getUserStreamerBalanceAction(testUserId, testStreamChannelId))
        .thenReturn(DBIO.successful(Some(userBalance)))
      
      when(mockStreamerEventRepository.updateCurrentConfidenceAmount(testEventId, eventBalance + transferAmount))
        .thenReturn(DBIO.successful(1))
      
      // Act & Assert
      val dbioAction = pollService.transferConfidenceVoteStreamer(testEventId, testUserId, testStreamChannelId, transferAmount)
      
      val thrown = intercept[IllegalStateException] {
        execDBIO(dbioAction)
      }
      thrown.getMessage must include("Negative amount for user balance")
      
      verify(mockStreamerEventRepository).getCurrentConfidenceAmountAction(testEventId)
      verify(mockUserStreamerStateRepository).getUserStreamerBalanceAction(testUserId, testStreamChannelId)
      verify(mockStreamerEventRepository).updateCurrentConfidenceAmount(testEventId, eventBalance + transferAmount)
    }
    
    "fail when update to event confidence amount returns 0_rows updated" in {
      // Arrange
      val eventBalance = 100
      val userBalance = 50
      val transferAmount = 20
      
      when(mockStreamerEventRepository.getCurrentConfidenceAmountAction(testEventId))
        .thenReturn(DBIO.successful(Some(eventBalance)))
      
      when(mockUserStreamerStateRepository.getUserStreamerBalanceAction(testUserId, testStreamChannelId))
        .thenReturn(DBIO.successful(Some(userBalance)))

      when(mockUserStreamerStateRepository.updateStreamerBalanceAction(testUserId, testStreamChannelId, userBalance - transferAmount)).
        thenReturn(DBIO.successful(1))

      when(mockStreamerEventRepository.updateCurrentConfidenceAmount(testEventId, eventBalance + transferAmount))
        .thenReturn(DBIO.successful(0)) // No rows updated



      // Act & Assert
      val dbioAction = pollService.transferConfidenceVoteStreamer(testEventId, testUserId, testStreamChannelId, transferAmount)
      
      val thrown = intercept[IllegalStateException] {
        execDBIO(dbioAction)
      }
      thrown.getMessage must include("Not updated done")
      
      verify(mockStreamerEventRepository).getCurrentConfidenceAmountAction(testEventId)
      verify(mockUserStreamerStateRepository).getUserStreamerBalanceAction(testUserId, testStreamChannelId)
      verify(mockStreamerEventRepository).updateCurrentConfidenceAmount(testEventId, eventBalance + transferAmount)
    }
    
    "fail when update to user streamer balance returns 0 rows updated" in {
      // Arrange
      val eventBalance = 100
      val userBalance = 50
      val transferAmount = 20
      
      when(mockStreamerEventRepository.getCurrentConfidenceAmountAction(testEventId))
        .thenReturn(DBIO.successful(Some(eventBalance)))
      
      when(mockUserStreamerStateRepository.getUserStreamerBalanceAction(testUserId, testStreamChannelId))
        .thenReturn(DBIO.successful(Some(userBalance)))
      
      when(mockStreamerEventRepository.updateCurrentConfidenceAmount(testEventId, eventBalance + transferAmount))
        .thenReturn(DBIO.successful(1))
      
      when(mockUserStreamerStateRepository.updateStreamerBalanceAction(testUserId, testStreamChannelId, userBalance - transferAmount))
        .thenReturn(DBIO.successful(0)) // No rows updated
      
      // Act & Assert
      val dbioAction = pollService.transferConfidenceVoteStreamer(testEventId, testUserId, testStreamChannelId, transferAmount)
      
      val thrown = intercept[IllegalStateException] {
        execDBIO(dbioAction)
      }
      thrown.getMessage must include("Not updated done")
      
      verify(mockStreamerEventRepository).getCurrentConfidenceAmountAction(testEventId)
      verify(mockUserStreamerStateRepository).getUserStreamerBalanceAction(testUserId, testStreamChannelId)
      verify(mockStreamerEventRepository).updateCurrentConfidenceAmount(testEventId, eventBalance + transferAmount)
      verify(mockUserStreamerStateRepository).updateStreamerBalanceAction(testUserId, testStreamChannelId, userBalance - transferAmount)
    }
    
    "handle transfer of zero amount correctly" in {
      // Arrange
      val eventBalance = 100
      val userBalance = 50
      val transferAmount = 0
      
      when(mockStreamerEventRepository.getCurrentConfidenceAmountAction(testEventId))
        .thenReturn(DBIO.successful(Some(eventBalance)))
      
      when(mockUserStreamerStateRepository.getUserStreamerBalanceAction(testUserId, testStreamChannelId))
        .thenReturn(DBIO.successful(Some(userBalance)))
      
      when(mockStreamerEventRepository.updateCurrentConfidenceAmount(testEventId, eventBalance + transferAmount))
        .thenReturn(DBIO.successful(1))
      
      when(mockUserStreamerStateRepository.updateStreamerBalanceAction(testUserId, testStreamChannelId, userBalance - transferAmount))
        .thenReturn(DBIO.successful(1))
      
      // Act & Assert
      val dbioAction = pollService.transferConfidenceVoteStreamer(testEventId, testUserId, testStreamChannelId, transferAmount)
      val result = execDBIO(dbioAction)
      result mustBe true
      
      verify(mockStreamerEventRepository).updateCurrentConfidenceAmount(testEventId, eventBalance + 0)
      verify(mockUserStreamerStateRepository).updateStreamerBalanceAction(testUserId, testStreamChannelId, userBalance - 0)
    }
    
    "handle negative transfer amount (from event to user) correctly" in {
      // Arrange
      val eventBalance = 100
      val userBalance = 50
      val transferAmount = -20 // Negative transfer (from event to user)
      
      when(mockStreamerEventRepository.getCurrentConfidenceAmountAction(testEventId))
        .thenReturn(DBIO.successful(Some(eventBalance)))
      
      when(mockUserStreamerStateRepository.getUserStreamerBalanceAction(testUserId, testStreamChannelId))
        .thenReturn(DBIO.successful(Some(userBalance)))
      
      when(mockStreamerEventRepository.updateCurrentConfidenceAmount(testEventId, eventBalance + transferAmount))
        .thenReturn(DBIO.successful(1))
      
      when(mockUserStreamerStateRepository.updateStreamerBalanceAction(testUserId, testStreamChannelId, userBalance - transferAmount))
        .thenReturn(DBIO.successful(1))
      
      // Act & Assert
      val dbioAction = pollService.transferConfidenceVoteStreamer(testEventId, testUserId, testStreamChannelId, transferAmount)
      val result = execDBIO(dbioAction)
      result mustBe true
      
      verify(mockStreamerEventRepository).updateCurrentConfidenceAmount(testEventId, eventBalance - 20)
      verify(mockUserStreamerStateRepository).updateStreamerBalanceAction(testUserId, testStreamChannelId, userBalance + 20)
    }
    
    "handle edge case of minimum integer value transfer correctly" in {
      // This test verifies behavior with Int.MinValue
      // In real scenarios, we'd expect validation before this method is called
      // But it's good to test boundary cases
      
      // Arrange
      val eventBalance = 100
      val userBalance = 500
      val transferAmount = Int.MinValue
      
      when(mockStreamerEventRepository.getCurrentConfidenceAmountAction(testEventId))
        .thenReturn(DBIO.successful(Some(eventBalance)))
      
      when(mockUserStreamerStateRepository.getUserStreamerBalanceAction(testUserId, testStreamChannelId))
        .thenReturn(DBIO.successful(Some(userBalance)))
      
      // Act & Assert
      val dbioAction = pollService.transferConfidenceVoteStreamer(testEventId, testUserId, testStreamChannelId, transferAmount)
      
      val thrown = intercept[IllegalStateException] {
        execDBIO(dbioAction)
      }
      thrown.getMessage must include("Negative balance for streamer event")
    }
  }
}