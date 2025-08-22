package services

import models.UserSmurf
import models.repository.{TournamentMatchRepository, UserSmurfRepository}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.*
import org.scalatestplus.play.guice.*
import play.api.test.*
import play.api.inject.guice.GuiceApplicationBuilder
import org.scalatest.{BeforeAndAfterEach, RecoverMethods}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.db.DBApi
import evolutioncomplete.ParticipantShared

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant
import play.api.db.evolutions.{Evolution, Evolutions, SimpleEvolutionsReader}

class UserSmurfServiceSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar with RecoverMethods with ScalaFutures {
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
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  lazy val db = app.injector.instanceOf[DBApi].database("default")

  override def beforeEach(): Unit = {
    super.beforeEach()

    // Apply the standard evolutions
    Evolutions.applyEvolutions(db)

    // Add test-specific data - only users, we'll create streamers in tests
    Evolutions.applyEvolutions(db,
      SimpleEvolutionsReader.forDefault(
        Evolution(
          21,
          """
            --- !Ups

            --- Add test data for YtStreamerRepositorySpec
            INSERT INTO users (user_id, user_name) VALUES (1, 'Test User 1');
            INSERT INTO users (user_id, user_name) VALUES (2, 'Test User 2');
            INSERT INTO tournaments (name, max_participants, registration_start_at, registration_end_at) VALUES ('Dummy Tournament', 8, '2025-08-01 10:00:00', '2025-08-05 10:00:00');
            INSERT INTO tournament_matches (match_id, tournament_id, first_user_id, second_user_id) VALUES (1, 1, 1, 2);

            """,
          s"""
            --- !Downs

            --- Remove test data
            DELETE FROM user_smurfs WHERE match_id IN (1);
            DELETE FROM users WHERE user_id IN (1, 2);
            DELETE FROM tournaments WHERE id IN (1);
            DELETE FROM tournament_matches WHERE match_id IN (1);
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

  "UserSmurfService" should {

    "record match smurfs for two players" in {
      val userSmurfService = app.injector.instanceOf[UserSmurfService]

      val matchId = 1
      val tournamentId = 1
      val firstUserId = 1
      val firstUserSmurf = "PlayerOne"
      val secondUserId = 2
      val secondUserSmurf = "PlayerTwo"

      val firstParticipant = ParticipantShared(firstUserId, "User1", Set(firstUserSmurf))
      val secondParticipant = ParticipantShared(secondUserId, "User2", Set(secondUserSmurf))

      val result = await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, firstParticipant, secondParticipant
      ))

      result must have size 2
      result.map(_.userId) must contain allOf(firstUserId, secondUserId)
      result.map(_.smurf) must contain allOf(firstUserSmurf, secondUserSmurf)
      result.foreach { smurf =>
        smurf.matchId mustBe matchId
        smurf.tournamentId mustBe tournamentId
      }
    }

    "get match smurfs" in {
      val userSmurfService = app.injector.instanceOf[UserSmurfService]

      val matchId = 1L
      val tournamentId = 1L
      val firstUserId = 1L
      val firstUserSmurf = "TestPlayer1"
      val secondUserId = 2L
      val secondUserSmurf = "TestPlayer2"

      val firstParticipant = ParticipantShared(firstUserId, "User1", Set(firstUserSmurf))
      val secondParticipant = ParticipantShared(secondUserId, "User2", Set(secondUserSmurf))

      // First record the smurfs
      await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, firstParticipant, secondParticipant
      ))

      // Then retrieve them
      val matchSmurfs = await(userSmurfService.getMatchSmurfs(matchId))
      matchSmurfs must have size 2
      matchSmurfs.map(_.smurf) must contain allOf(firstUserSmurf, secondUserSmurf)
    }

    "get user smurf in match" in {
      val userSmurfService = app.injector.instanceOf[UserSmurfService]

      val matchId = 1L
      val tournamentId = 1L
      val userId = 1L
      val userSmurf = "UniquePlayer"
      val otherUserId = 2L
      val otherUserSmurf = "OtherPlayer"

      val firstParticipant = ParticipantShared(userId, "User1", Set(userSmurf))
      val secondParticipant = ParticipantShared(otherUserId, "User2", Set(otherUserSmurf))

      // Record smurfs for a match
      await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, firstParticipant, secondParticipant
      ))

      // Get specific user's smurf in the match
      val result = await(userSmurfService.getUserSmurfInMatch(matchId, userId))
      result mustBe defined
      result.get.smurf mustBe userSmurf
      result.get.userId mustBe userId
    }

    "check if match smurfs are recorded" in {
      val userSmurfService = app.injector.instanceOf[UserSmurfService]

      val matchId = 1L
      val tournamentId = 1L

      // Initially no smurfs should be recorded
      val beforeRecording = await(userSmurfService.hasMatchSmurfsRecorded(matchId))
      beforeRecording mustBe false

      val firstParticipant = ParticipantShared(1L, "User1", Set("Player1"))
      val secondParticipant = ParticipantShared(2L, "User2", Set("Player2"))

      // Record smurfs
      await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, firstParticipant, secondParticipant
      ))

      // Now smurfs should be recorded
      val afterRecording = await(userSmurfService.hasMatchSmurfsRecorded(matchId))
      afterRecording mustBe true
    }

    "get match smurf count" in {
      val userSmurfService = app.injector.instanceOf[UserSmurfService]

      val matchId = 1L
      val tournamentId = 1L

      // Initially count should be 0
      val initialCount = await(userSmurfService.getMatchSmurfCount(matchId))
      initialCount mustBe 0

      val firstParticipant = ParticipantShared(1L, "User1", Set("TestPlayer1"))
      val secondParticipant = ParticipantShared(2L, "User2", Set("TestPlayer2"))

      // Record smurfs
      await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, firstParticipant, secondParticipant
      ))

      // Count should be 2
      val finalCount = await(userSmurfService.getMatchSmurfCount(matchId))
      finalCount mustBe 2
    }

    "delete match smurfs" in {
      val userSmurfService = app.injector.instanceOf[UserSmurfService]

      val matchId = 1L
      val tournamentId = 1L

      val firstParticipant = ParticipantShared(1L, "User1", Set("PlayerA"))
      val secondParticipant = ParticipantShared(2L, "User2", Set("PlayerB"))

      // Record smurfs
      await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, firstParticipant, secondParticipant
      ))

      // Verify they exist
      val beforeDelete = await(userSmurfService.getMatchSmurfCount(matchId))
      beforeDelete mustBe 2

      // Delete them
      val deletedCount = await(userSmurfService.deleteMatchSmurfs(matchId))
      deletedCount mustBe 2

      // Verify they're gone
      val afterDelete = await(userSmurfService.getMatchSmurfCount(matchId))
      afterDelete mustBe 0
    }
  }

  private def await[T](future: Future[T]): T = {
    scala.concurrent.Await.result(future, 5.seconds)
  }
}
