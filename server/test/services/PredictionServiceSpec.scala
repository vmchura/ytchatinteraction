package services

import models.{PredictionBetType, PredictionEvent, PredictionMatchup, PredictionMatchupStatus}
import models.repository.{PredictionBetRepository, PredictionEventRepository, PredictionMatchupRepository, PredictionUserScoreRepository, UserRepository}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.db.evolutions.Evolutions
import play.api.db.{DBApi, Database}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.Injecting

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.Await

class PredictionServiceSpec
    extends PlaySpec with GuiceOneAppPerSuite with Injecting with BeforeAndAfterEach with MockitoSugar {

  override def fakeApplication(): Application = {
    GuiceApplicationBuilder()
      .configure(
        "slick.dbs.default.profile" -> "slick.jdbc.H2Profile$",
        "slick.dbs.default.db.driver" -> "org.h2.Driver",
        "slick.dbs.default.db.url" -> "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "slick.dbs.default.db.user" -> "sa",
        "slick.dbs.default.db.password" -> "",
        "play.evolutions.db.default.enabled" -> true,
        "play.evolutions.db.default.autoApply" -> true
      )
      .build()
  }

  lazy val db: Database = app.injector.instanceOf[DBApi].database("default")
  lazy val service = app.injector.instanceOf[PredictionService]
  lazy val userRepository = app.injector.instanceOf[UserRepository]
  lazy val eventRepository = app.injector.instanceOf[PredictionEventRepository]
  lazy val matchupRepository = app.injector.instanceOf[PredictionMatchupRepository]
  lazy val betRepository = app.injector.instanceOf[PredictionBetRepository]
  lazy val scoreRepository = app.injector.instanceOf[PredictionUserScoreRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Evolutions.applyEvolutions(db)
  }

  override def afterEach(): Unit = {
    Evolutions.cleanupEvolutions(db)
    super.afterEach()
  }

  private def createUser(name: String): Long =
    Await.result(userRepository.createWithAlias(name), 5.seconds).userId

  private def createEvent(name: String): Long =
    Await.result(eventRepository.create(PredictionEvent(name = name)), 5.seconds).id

  private def createGroupMatchup(eventId: Long): Long =
    Await.result(matchupRepository.create(PredictionMatchup(
      eventId = eventId,
      round = 1,
      matchLabel = "Group A",
      betType = PredictionBetType.GroupPosition,
      player1Name = Some("W"),
      player2Name = Some("X"),
      player3Name = Some("Y"),
      player4Name = Some("Z")
    )), 5.seconds).id

  private def createMatchMatchup(eventId: Long): Long =
    Await.result(matchupRepository.create(PredictionMatchup(
      eventId = eventId,
      round = 4,
      matchLabel = "Final",
      betType = PredictionBetType.MatchScore,
      player1Name = Some("Serral"),
      player2Name = Some("Maru")
    )), 5.seconds).id

  "PredictionService.savePrediction" should {
    "save a valid group permutation" in {
      val userId = createUser("User A")
      val eventId = createEvent("Event")
      val matchupId = createGroupMatchup(eventId)

      val result = Await.result(
        service.savePrediction(userId, matchupId, Seq("player3", "player1", "player4", "player2")),
        5.seconds
      )
      result mustBe a[Right[_, _]]
      result.toOption.get.ranking mustBe Seq("player3", "player1", "player4", "player2")
    }

    "replace a previous prediction on re-save" in {
      val userId = createUser("User A")
      val eventId = createEvent("Event")
      val matchupId = createGroupMatchup(eventId)

      Await.result(service.savePrediction(userId, matchupId, Seq("player1", "player2", "player3", "player4")), 5.seconds)
      val second = Await.result(
        service.savePrediction(userId, matchupId, Seq("player4", "player3", "player2", "player1")),
        5.seconds
      )
      second.toOption.get.ranking mustBe Seq("player4", "player3", "player2", "player1")
    }

    "reject a permutation with duplicate players" in {
      val userId = createUser("User A")
      val eventId = createEvent("Event")
      val matchupId = createGroupMatchup(eventId)

      val result = Await.result(
        service.savePrediction(userId, matchupId, Seq("player1", "player1", "player2", "player3")),
        5.seconds
      )
      result mustBe a[Left[_, _]]
      result.left.toOption.get must include("one position")
    }

    "reject saving once the matchup is locked" in {
      val userId = createUser("User A")
      val eventId = createEvent("Event")
      val matchupId = createGroupMatchup(eventId)
      Await.result(matchupRepository.updateStatus(matchupId, PredictionMatchupStatus.Locked), 5.seconds)

      val result = Await.result(
        service.savePrediction(userId, matchupId, Seq("player1", "player2", "player3", "player4")),
        5.seconds
      )
      result mustBe a[Left[_, _]]
      result.left.toOption.get must include("not open")
    }
  }

  "PredictionService.scoreMatchup" should {
    "score a group using exact position (2), advance (1) and eliminated (1) rules" in {
      val userA = createUser("User A")
      val userB = createUser("User B")
      val eventId = createEvent("Event")
      val matchupId = createGroupMatchup(eventId)

      // Actual result: 1st W(player1), 2nd Y(player3), 3rd Z(player4), 4th X(player2)
      val resultJson = Json.parse("""{"positions":{"1":"player1","2":"player3","3":"player4","4":"player2"}}""")

      // User A predicted [W, X, Y, Z] -> [player1, player2, player3, player4]
      Await.result(service.savePrediction(userA, matchupId, Seq("player1", "player2", "player3", "player4")), 5.seconds)
      // User B predicted [X, W, Z, Y] -> [player2, player1, player4, player3]
      Await.result(service.savePrediction(userB, matchupId, Seq("player2", "player1", "player4", "player3")), 5.seconds)

      Await.result(matchupRepository.updateStatus(matchupId, PredictionMatchupStatus.Locked), 5.seconds)

      val scored = Await.result(service.scoreMatchup(matchupId, resultJson), 5.seconds)
      scored mustBe a[Right[_, _]]

      val updated = Await.result(matchupRepository.findById(matchupId), 5.seconds).get
      updated.status mustBe PredictionMatchupStatus.Scored
      updated.result must not be empty

      // User A: W exact (2) + W advances (1) + Z eliminated (1) = 4
      val scoreA = Await.result(scoreRepository.findByUserAndEvent(userA, eventId), 5.seconds)
      scoreA.get.totalScore mustBe 4
      // User B: Z exact (2) + W advances (1) + Z eliminated (1) = 4
      val scoreB = Await.result(scoreRepository.findByUserAndEvent(userB, eventId), 5.seconds)
      scoreB.get.totalScore mustBe 4
    }

    "score a match: 5 points for the exact score (3 winner + 2 exact)" in {
      val userId = createUser("User A")
      val eventId = createEvent("Event")
      val matchupId = createMatchMatchup(eventId)

      Await.result(service.savePrediction(userId, matchupId, Seq("4-2")), 5.seconds)
      Await.result(matchupRepository.updateStatus(matchupId, PredictionMatchupStatus.Locked), 5.seconds)
      val resultJson = Json.parse("""{"score":"4-2"}""")

      val scored = Await.result(service.scoreMatchup(matchupId, resultJson), 5.seconds)
      scored mustBe a[Right[_, _]]

      val score = Await.result(scoreRepository.findByUserAndEvent(userId, eventId), 5.seconds)
      score.get.totalScore mustBe 5
    }

    "score a match: 3 points for correct winner with different score" in {
      val userId = createUser("User A")
      val eventId = createEvent("Event")
      val matchupId = createMatchMatchup(eventId)

      Await.result(service.savePrediction(userId, matchupId, Seq("4-1")), 5.seconds)
      Await.result(matchupRepository.updateStatus(matchupId, PredictionMatchupStatus.Locked), 5.seconds)
      val resultJson = Json.parse("""{"score":"4-2"}""")

      val scored = Await.result(service.scoreMatchup(matchupId, resultJson), 5.seconds)
      scored mustBe a[Right[_, _]]

      val score = Await.result(scoreRepository.findByUserAndEvent(userId, eventId), 5.seconds)
      score.get.totalScore mustBe 3
    }

    "reject a group result that is not a permutation of the players" in {
      val eventId = createEvent("Event")
      val matchupId = createGroupMatchup(eventId)
      Await.result(matchupRepository.updateStatus(matchupId, PredictionMatchupStatus.Locked), 5.seconds)

      val resultJson = Json.parse("""{"positions":{"1":"player1","2":"player1","3":"player2","4":"player3"}}""")
      val scored = Await.result(service.scoreMatchup(matchupId, resultJson), 5.seconds)
      scored mustBe a[Left[_, _]]
      scored.left.toOption.get must include("permutation")
    }
  }
}
