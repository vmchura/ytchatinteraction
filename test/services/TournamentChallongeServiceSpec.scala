package services

import models.{Tournament, TournamentStatus, User}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.RecoverMethods
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.test.Helpers._

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class TournamentChallongeServiceSpec extends PlaySpec with MockitoSugar with BeforeAndAfterEach with ScalaFutures with RecoverMethods {

  // Mock dependencies
  private val mockWsClient = mock[WSClient]
  private val mockWsRequest = mock[WSRequest]
  private val mockWsResponse = mock[WSResponse]
  private val mockConfiguration = mock[Configuration]
  
  // Service under test
  private var challongeService: TournamentChallongeService = _
  
  // Test data
  private val testApiKey = "test-api-key"
  private val testBaseUrl = "https://api.challonge.com/v1"
  private val testTournament = Tournament(
    id = 1L,
    name = "Test Tournament",
    description = Some("A test tournament"),
    maxParticipants = 8,
    registrationStartAt = Instant.now().minusSeconds(3600),
    registrationEndAt = Instant.now().plusSeconds(3600),
    tournamentStartAt = Some(Instant.now()),
    status = TournamentStatus.RegistrationOpen
  )
  
  private val testUsers = List(
    User(1L, "Player1"),
    User(2L, "Player2"),
    User(3L, "Player3")
  )
  
  private val challongeTournamentId = 12345L
  
  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockWsClient, mockWsRequest, mockWsResponse, mockConfiguration)
    
    // Setup configuration mocks
    when(mockConfiguration.get[String]("challonge.api.key")).thenReturn(testApiKey)
    when(mockConfiguration.getOptional[String]("challonge.api.baseUrl")).thenReturn(Some(testBaseUrl))
    
    // Setup default WS request chain
    when(mockWsClient.url(any[String]())).thenReturn(mockWsRequest)
    when(mockWsRequest.addHttpHeaders(any[(String, String)]())).thenReturn(mockWsRequest)
    when(mockWsRequest.addQueryStringParameters(any[(String, String)]())).thenReturn(mockWsRequest)
    
    challongeService = new TournamentChallongeServiceImpl(mockWsClient, mockConfiguration)
  }

  "TournamentChallongeService#createChallongeTournament" should {
    "successfully create a tournament in Challonge" in {
      // Arrange
      val responseJson = Json.obj(
        "tournament" -> Json.obj(
          "id" -> challongeTournamentId,
          "name" -> testTournament.name,
          "url" -> "test_tournament_1"
        )
      )
      
      when(mockWsResponse.status).thenReturn(201)
      when(mockWsResponse.json).thenReturn(responseJson)
      when(mockWsResponse.body).thenReturn(responseJson.toString)
      when(mockWsRequest.post(any[JsValue]())).thenReturn(Future.successful(mockWsResponse))
      
      // Mock participant addition calls
      when(mockWsRequest.post(any[JsValue]())).thenReturn(
        Future.successful(mockWsResponse),
        Future.successful(mockWsResponse),
        Future.successful(mockWsResponse),
        Future.successful(mockWsResponse)
      )
      
      // Act
      val result = challongeService.createChallongeTournament(testTournament, testUsers).futureValue
      
      // Assert
      result must be(challongeTournamentId)
      verify(mockWsClient).url(s"$testBaseUrl/tournaments.json")
      verify(mockWsRequest).addQueryStringParameters("api_key" -> testApiKey)
    }
    
    "handle API errors gracefully" in {
      // Arrange
      when(mockWsResponse.status).thenReturn(400)
      when(mockWsResponse.body).thenReturn("Bad Request")
      when(mockWsRequest.post(any[JsValue]())).thenReturn(Future.successful(mockWsResponse))
      
      // Act & Assert
      recoverToExceptionIf[RuntimeException] {
        challongeService.createChallongeTournament(testTournament, testUsers)
      }.futureValue.getMessage must include("Failed to create tournament in Challonge")
    }
    
    "include correct tournament parameters" in {
      // Arrange
      val responseJson = Json.obj(
        "tournament" -> Json.obj(
          "id" -> challongeTournamentId,
          "name" -> testTournament.name
        )
      )
      
      when(mockWsResponse.status).thenReturn(201)
      when(mockWsResponse.json).thenReturn(responseJson)
      when(mockWsResponse.body).thenReturn(responseJson.toString)
      when(mockWsRequest.post(any[JsValue]())).thenReturn(Future.successful(mockWsResponse))
      
      // Capture the tournament data sent to API
      val tournamentDataCaptor = org.mockito.ArgumentCaptor.forClass(classOf[JsValue])
      
      // Act
      challongeService.createChallongeTournament(testTournament, testUsers).futureValue
      
      // Assert
      verify(mockWsRequest).post(tournamentDataCaptor.capture())
      val sentData = tournamentDataCaptor.getValue
      (sentData \ "tournament" \ "name").as[String] must be(testTournament.name)
      (sentData \ "tournament" \ "tournament_type").as[String] must be("round robin")
      (sentData \ "tournament" \ "open_signup").as[Boolean] must be(false)
    }
  }

  "TournamentChallongeService#addParticipant" should {
    "successfully add a participant" in {
      // Arrange
      val user = testUsers.head
      when(mockWsResponse.status).thenReturn(201)
      when(mockWsRequest.post(any[JsValue]())).thenReturn(Future.successful(mockWsResponse))
      
      // Act
      val result = challongeService.addParticipant(challongeTournamentId, user).futureValue
      
      // Assert
      result must be(true)
      verify(mockWsClient).url(s"$testBaseUrl/tournaments/$challongeTournamentId/participants.json")
    }
    
    "handle participant addition failure" in {
      // Arrange
      val user = testUsers.head
      when(mockWsResponse.status).thenReturn(400)
      when(mockWsResponse.body).thenReturn("Bad Request")
      when(mockWsRequest.post(any[JsValue]())).thenReturn(Future.successful(mockWsResponse))
      
      // Act
      val result = challongeService.addParticipant(challongeTournamentId, user).futureValue
      
      // Assert
      result must be(false)
    }
    
    "include correct participant data" in {
      // Arrange
      val user = testUsers.head
      when(mockWsResponse.status).thenReturn(201)
      when(mockWsRequest.post(any[JsValue]())).thenReturn(Future.successful(mockWsResponse))
      
      val participantDataCaptor = org.mockito.ArgumentCaptor.forClass(classOf[JsValue])
      
      // Act
      challongeService.addParticipant(challongeTournamentId, user).futureValue
      
      // Assert
      verify(mockWsRequest).post(participantDataCaptor.capture())
      val sentData = participantDataCaptor.getValue
      (sentData \ "participant" \ "name").as[String] must be(user.userName)
      (sentData \ "participant" \ "misc").as[String] must include(user.userId.toString)
    }
  }

  "TournamentChallongeService#updateChallongeTournament" should {
    "successfully update a tournament" in {
      // Arrange
      when(mockWsResponse.status).thenReturn(200)
      when(mockWsRequest.put(any[JsValue]())).thenReturn(Future.successful(mockWsResponse))
      
      // Act
      val result = challongeService.updateChallongeTournament(challongeTournamentId, testTournament).futureValue
      
      // Assert
      result must be(true)
      verify(mockWsClient).url(s"$testBaseUrl/tournaments/$challongeTournamentId.json")
    }
    
    "handle update failure" in {
      // Arrange
      when(mockWsResponse.status).thenReturn(404)
      when(mockWsResponse.body).thenReturn("Not Found")
      when(mockWsRequest.put(any[JsValue]())).thenReturn(Future.successful(mockWsResponse))
      
      // Act
      val result = challongeService.updateChallongeTournament(challongeTournamentId, testTournament).futureValue
      
      // Assert
      result must be(false)
    }
  }

  "TournamentChallongeService#startChallongeTournament" should {
    "successfully start a tournament" in {
      // Arrange
      when(mockWsResponse.status).thenReturn(200)
      when(mockWsRequest.post(any[String]())).thenReturn(Future.successful(mockWsResponse))
      
      // Act
      val result = challongeService.startChallongeTournament(challongeTournamentId).futureValue
      
      // Assert
      result must be(true)
      verify(mockWsClient).url(s"$testBaseUrl/tournaments/$challongeTournamentId/start.json")
    }
    
    "handle start failure" in {
      // Arrange
      when(mockWsResponse.status).thenReturn(422)
      when(mockWsResponse.body).thenReturn("Unprocessable Entity")
      when(mockWsRequest.post(any[String]())).thenReturn(Future.successful(mockWsResponse))
      
      // Act
      val result = challongeService.startChallongeTournament(challongeTournamentId).futureValue
      
      // Assert
      result must be(false)
    }
  }

  "TournamentChallongeService configuration" should {
    "use default base URL when not configured" in {
      // Arrange
      when(mockConfiguration.getOptional[String]("challonge.api.baseUrl")).thenReturn(None)
      
      // Act
      val service = new TournamentChallongeServiceImpl(mockWsClient, mockConfiguration)
      
      // The service should still work with default URL
      when(mockWsResponse.status).thenReturn(201)
      when(mockWsResponse.json).thenReturn(Json.obj("tournament" -> Json.obj("id" -> 123L)))
      when(mockWsResponse.body).thenReturn("{}")
      when(mockWsRequest.post(any[JsValue]())).thenReturn(Future.successful(mockWsResponse))
      
      // Assert
      service.createChallongeTournament(testTournament, List.empty).futureValue must be(123L)
    }
    
    "handle network errors gracefully" in {
      // Arrange
      when(mockWsRequest.post(any[JsValue]())).thenReturn(Future.failed(new RuntimeException("Network error")))
      
      // Act & Assert
      recoverToExceptionIf[RuntimeException] {
        challongeService.createChallongeTournament(testTournament, testUsers)
      }.futureValue.getMessage must include("Network error")
    }
  }

  "TournamentChallongeService URL generation" should {
    "generate valid tournament URLs" in {
      // This is tested indirectly through the createChallongeTournament method
      // The URL generation is internal but we can verify it doesn't cause issues
      
      val tournamentWithSpecialChars = testTournament.copy(name = "Test Tournament!!! @#$%")
      
      when(mockWsResponse.status).thenReturn(201)
      when(mockWsResponse.json).thenReturn(Json.obj("tournament" -> Json.obj("id" -> 123L)))
      when(mockWsResponse.body).thenReturn("{}")
      when(mockWsRequest.post(any[JsValue]())).thenReturn(Future.successful(mockWsResponse))
      
      // Act
      val result = challongeService.createChallongeTournament(tournamentWithSpecialChars, List.empty).futureValue
      
      // Assert
      result must be(123L)
    }
  }
}
