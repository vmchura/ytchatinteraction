package services

import models.UserSmurf
import models.repository.UserSmurfRepository
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.inject.guice.GuiceApplicationBuilder
import org.scalatest.BeforeAndAfterEach
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

class UserSmurfServiceSpec extends PlaySpec with GuiceOneAppPerTest with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  "UserSmurfService" should {

    "record match smurfs for two players" in {
      val app = GuiceApplicationBuilder().build()
      val userSmurfService = app.injector.instanceOf[UserSmurfService]
      val userSmurfRepository = app.injector.instanceOf[UserSmurfRepository]

      val matchId = 1L
      val tournamentId = 1L
      val firstUserId = 100L
      val firstUserSmurf = "PlayerOne"
      val secondUserId = 200L
      val secondUserSmurf = "PlayerTwo"

      val result = await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, firstUserId, firstUserSmurf, secondUserId, secondUserSmurf
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
      val app = GuiceApplicationBuilder().build()
      val userSmurfService = app.injector.instanceOf[UserSmurfService]

      val matchId = 2L
      val tournamentId = 1L
      val firstUserId = 101L
      val firstUserSmurf = "TestPlayer1"
      val secondUserId = 201L
      val secondUserSmurf = "TestPlayer2"

      // First record the smurfs
      await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, firstUserId, firstUserSmurf, secondUserId, secondUserSmurf
      ))

      // Then retrieve them
      val matchSmurfs = await(userSmurfService.getMatchSmurfs(matchId))
      matchSmurfs must have size 2
      matchSmurfs.map(_.smurf) must contain allOf(firstUserSmurf, secondUserSmurf)
    }

    "get user smurf in match" in {
      val app = GuiceApplicationBuilder().build()
      val userSmurfService = app.injector.instanceOf[UserSmurfService]

      val matchId = 3L
      val tournamentId = 1L
      val userId = 102L
      val userSmurf = "UniquePlayer"
      val otherUserId = 202L
      val otherUserSmurf = "OtherPlayer"

      // Record smurfs for a match
      await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, userId, userSmurf, otherUserId, otherUserSmurf
      ))

      // Get specific user's smurf in the match
      val result = await(userSmurfService.getUserSmurfInMatch(matchId, userId))
      result mustBe defined
      result.get.smurf mustBe userSmurf
      result.get.userId mustBe userId
    }

    "check if match smurfs are recorded" in {
      val app = GuiceApplicationBuilder().build()
      val userSmurfService = app.injector.instanceOf[UserSmurfService]

      val matchId = 4L
      val tournamentId = 1L

      // Initially no smurfs should be recorded
      val beforeRecording = await(userSmurfService.hasMatchSmurfsRecorded(matchId))
      beforeRecording mustBe false

      // Record smurfs
      await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, 103L, "Player1", 203L, "Player2"
      ))

      // Now smurfs should be recorded
      val afterRecording = await(userSmurfService.hasMatchSmurfsRecorded(matchId))
      afterRecording mustBe true
    }

    "get match smurf count" in {
      val app = GuiceApplicationBuilder().build()
      val userSmurfService = app.injector.instanceOf[UserSmurfService]

      val matchId = 5L
      val tournamentId = 1L

      // Initially count should be 0
      val initialCount = await(userSmurfService.getMatchSmurfCount(matchId))
      initialCount mustBe 0

      // Record smurfs
      await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, 104L, "TestPlayer1", 204L, "TestPlayer2"
      ))

      // Count should be 2
      val finalCount = await(userSmurfService.getMatchSmurfCount(matchId))
      finalCount mustBe 2
    }

    "delete match smurfs" in {
      val app = GuiceApplicationBuilder().build()
      val userSmurfService = app.injector.instanceOf[UserSmurfService]

      val matchId = 6L
      val tournamentId = 1L

      // Record smurfs
      await(userSmurfService.recordMatchSmurfs(
        matchId, tournamentId, 105L, "PlayerA", 205L, "PlayerB"
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
