package models.repository

import models.{PredictionBet, PredictionBetType, PredictionEvent, PredictionMatchup}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.db.evolutions.Evolutions
import play.api.db.{DBApi, Database}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.*
import play.api.test.Injecting

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.Await

class PredictionBetRepositorySpec
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
  lazy val betRepository = app.injector.instanceOf[PredictionBetRepository]
  lazy val userRepository = app.injector.instanceOf[UserRepository]
  lazy val eventRepository = app.injector.instanceOf[PredictionEventRepository]
  lazy val matchupRepository = app.injector.instanceOf[PredictionMatchupRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Evolutions.applyEvolutions(db)
  }

  override def afterEach(): Unit = {
    Evolutions.cleanupEvolutions(db)
    super.afterEach()
  }

  /** Creates a user, an event and a group matchup; returns (userId, matchupId). */
  private def setupMatchup(): (Long, Long) = {
    val user = Await.result(userRepository.createWithAlias("Pred Test User"), 5.seconds)
    val event = Await.result(eventRepository.create(PredictionEvent(name = "Test Event")), 5.seconds)
    val matchup = Await.result(
      matchupRepository.create(PredictionMatchup(
        eventId = event.id,
        round = 1,
        matchLabel = "Group A",
        betType = PredictionBetType.GroupPosition
      )),
      5.seconds
    )
    (user.userId, matchup.id)
  }

  "PredictionBetRepository" should {
    "upsert a prediction for a user and matchup" in {
      val (userId, matchupId) = setupMatchup()
      val saved = Await.result(betRepository.upsert(userId, matchupId, Seq("player3", "player1", "player4", "player2")), 5.seconds)

      saved.userId mustBe userId
      saved.matchupId mustBe matchupId
      saved.ranking mustBe Seq("player3", "player1", "player4", "player2")

      val found = Await.result(betRepository.findByUserAndMatchup(userId, matchupId), 5.seconds)
      found must not be empty
      found.get.ranking mustBe Seq("player3", "player1", "player4", "player2")
    }

    "replace the previous prediction when the user saves again" in {
      val (userId, matchupId) = setupMatchup()
      Await.result(betRepository.upsert(userId, matchupId, Seq("player1", "player2", "player3", "player4")), 5.seconds)
      val replaced = Await.result(betRepository.upsert(userId, matchupId, Seq("player4", "player3", "player2", "player1")), 5.seconds)

      replaced.ranking mustBe Seq("player4", "player3", "player2", "player1")
      val all = Await.result(betRepository.findByMatchup(matchupId), 5.seconds)
      all.size mustBe 1
      all.head.ranking mustBe Seq("player4", "player3", "player2", "player1")
    }

    "return None for a user who has not predicted" in {
      val (userId, matchupId) = setupMatchup()
      val found = Await.result(betRepository.findByUserAndMatchup(userId, matchupId), 5.seconds)
      found mustBe None
    }

    "delete the prediction of a user and matchup" in {
      val (userId, matchupId) = setupMatchup()
      Await.result(betRepository.upsert(userId, matchupId, Seq("player1", "player2", "player3", "player4")), 5.seconds)
      val deleted = Await.result(betRepository.deleteByUserAndMatchup(userId, matchupId), 5.seconds)
      deleted mustBe 1
      Await.result(betRepository.findByUserAndMatchup(userId, matchupId), 5.seconds) mustBe None
    }
  }
}
