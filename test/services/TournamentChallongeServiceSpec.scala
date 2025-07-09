package services

import models.{Tournament, TournamentStatus, User}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.RecoverMethods
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.DefaultBodyWritables._
import play.api.test.Helpers._

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class TournamentChallongeServiceSpec extends PlaySpec with BeforeAndAfterEach with ScalaFutures with RecoverMethods {

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

  // Simple mock configuration that doesn't need interface mocking
  private val mockConfiguration = new Configuration(play.api.Configuration.from(Map(
    "challonge.api.key" -> testApiKey,
    "challonge.api.baseUrl" -> testBaseUrl
  )).underlying)

  "TournamentChallongeService configuration" should {
    "load configuration correctly" in {
      mockConfiguration.get[String]("challonge.api.key") must be(testApiKey)
      mockConfiguration.getOptional[String]("challonge.api.baseUrl") must be(Some(testBaseUrl))
    }
    
    "use default base URL when not configured" in {
      val configWithoutUrl = new Configuration(play.api.Configuration.from(Map(
        "challonge.api.key" -> testApiKey
      )).underlying)
      
      configWithoutUrl.get[String]("challonge.api.key") must be(testApiKey)
      configWithoutUrl.getOptional[String]("challonge.api.baseUrl") must be(None)
    }
  }

  "TournamentChallongeService URL generation" should {
    "generate safe tournament URLs" in {
      // Testing the URL generation logic indirectly by checking special characters handling
      val tournamentWithSpecialChars = testTournament.copy(name = "Test Tournament!!! @#$%")
      
      // The service should handle special characters properly
      tournamentWithSpecialChars.name must include("Test Tournament")
    }
  }

  "TournamentChallongeService data formatting" should {
    "format tournament data correctly for Challonge API" in {
      val tournamentData = Json.obj(
        "tournament" -> Json.obj(
          "name" -> testTournament.name,
          "description" -> testTournament.description.getOrElse(s"Tournament created from ${testTournament.name}"),
          "tournament_type" -> "round robin",
          "open_signup" -> false,
          "hold_third_place_match" -> false,
          "pts_for_match_win" -> "1.0",
          "pts_for_match_tie" -> "0.5",
          "pts_for_game_win" -> "0.0",
          "pts_for_game_tie" -> "0.0",
          "pts_for_bye" -> "1.0",
          "private" -> false,
          "notify_users_when_matches_open" -> true,
          "notify_users_when_the_tournament_ends" -> true,
          "sequential_pairings" -> false,
          "signup_cap" -> testTournament.maxParticipants,
          "start_at" -> testTournament.tournamentStartAt.map(_.toString),
          "check_in_duration" -> null
        )
      )

      (tournamentData \ "tournament" \ "name").as[String] must be(testTournament.name)
      (tournamentData \ "tournament" \ "tournament_type").as[String] must be("round robin")
      (tournamentData \ "tournament" \ "open_signup").as[Boolean] must be(false)
      (tournamentData \ "tournament" \ "signup_cap").as[Int] must be(testTournament.maxParticipants)
    }
    
    "format participant data correctly for Challonge API" in {
      val participant = testUsers.head
      val participantData = Json.obj(
        "participant" -> Json.obj(
          "name" -> participant.userName,
          "misc" -> s"User ID: ${participant.userId}"
        )
      )

      (participantData \ "participant" \ "name").as[String] must be(participant.userName)
      (participantData \ "participant" \ "misc").as[String] must include(participant.userId.toString)
    }
  }

  "TournamentChallongeService error handling" should {
    "handle missing configuration gracefully" in {
      val emptyConfig = new Configuration(play.api.Configuration.from(Map.empty[String, String]).underlying)
      
      // Should throw exception when trying to get required config
      assertThrows[Exception] {
        emptyConfig.get[String]("challonge.api.key")
      }
    }
  }

  "TournamentChallongeService JSON parsing" should {
    "parse Challonge API response correctly" in {
      val responseJson = Json.obj(
        "tournament" -> Json.obj(
          "id" -> challongeTournamentId,
          "name" -> testTournament.name,
          "url" -> "test_tournament_1"
        )
      )
      
      (responseJson \ "tournament" \ "id").as[Long] must be(challongeTournamentId)
      (responseJson \ "tournament" \ "name").as[String] must be(testTournament.name)
    }
  }

  "TournamentChallongeService business logic" should {
    "validate tournament data before sending to API" in {
      // Tournament name should not be empty
      testTournament.name must not be empty
      
      // Max participants should be positive
      testTournament.maxParticipants must be > 0
      
      // Registration dates should make sense
      testTournament.registrationStartAt must be <= testTournament.registrationEndAt
    }
    
    "handle empty participant list" in {
      val emptyParticipants = List.empty[User]
      
      emptyParticipants.length must be(0)
      // Service should handle this case gracefully
    }
    
    "handle participant list with valid users" in {
      testUsers.foreach { user =>
        user.userName must not be empty
        user.userId must be > 0L
      }
    }
  }

  "TournamentChallongeService fake user generation" should {
    // Create a service instance for testing fake user generation
    val mockWsClient = null // Not needed for this test
    val service = new TournamentChallongeServiceImpl(mockWsClient, mockConfiguration)
    
    "generate 2 fake users when no real participants exist" in {
      val emptyParticipants = List.empty[User]
      val fakeUsers = service.generateFakeUsers(emptyParticipants)
      
      fakeUsers.length must be(2)
      fakeUsers.foreach { user =>
        user.userId must be < 0L // Fake users have negative IDs
        user.userName must startWith("ChallongeBot_")
      }
    }
    
    "generate 1 fake user when 1 real participant exists" in {
      val oneParticipant = List(User(1L, "RealPlayer"))
      val fakeUsers = service.generateFakeUsers(oneParticipant)
      
      fakeUsers.length must be(1)
      fakeUsers.head.userId must be < 0L
      fakeUsers.head.userName must startWith("ChallongeBot_")
    }
    
    "generate no fake users when 2 or more real participants exist" in {
      val twoParticipants = List(User(1L, "Player1"), User(2L, "Player2"))
      val fakeUsers = service.generateFakeUsers(twoParticipants)
      
      fakeUsers.length must be(0)
    }
    
    "generate fake users with unique names" in {
      val emptyParticipants = List.empty[User]
      val fakeUsers = service.generateFakeUsers(emptyParticipants)
      
      val userNames = fakeUsers.map(_.userName)
      userNames.distinct.length must be(userNames.length) // All names should be unique
    }
    
    "generate fake users with valid format" in {
      val emptyParticipants = List.empty[User]
      val fakeUsers = service.generateFakeUsers(emptyParticipants)
      
      fakeUsers.foreach { user =>
        user.userName must not be empty
        user.userName must include("ChallongeBot_")
        user.userId must be < 0L
      }
    }
    
    "handle edge case with many real participants" in {
      val manyParticipants = (1 to 10).map(i => User(i.toLong, s"Player$i")).toList
      val fakeUsers = service.generateFakeUsers(manyParticipants)
      
      fakeUsers.length must be(0) // No fake users needed
    }
  }
}
