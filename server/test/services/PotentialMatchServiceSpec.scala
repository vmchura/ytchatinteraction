package services

import models.{
  PotentialMatch,
  PotentialMatchStatus,
  UserAvailability,
  AvailabilityStatus
}
import models.repository.{PotentialMatchRepository, UserAvailabilityRepository}
import models.User
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import java.time.{DayOfWeek, Instant, LocalDateTime, ZoneId}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class PotentialMatchServiceSpec extends PlaySpec {

  val testFirstUser = User(1L, "Player1")
  val testSecondUser = User(2L, "Player2")
  val calculationTimeThursday = Instant.parse("2026-02-05T10:00:00Z")
  val madridZone = "Europe/Madrid"
  val limaZone = "America/Lima"

  private def createTestAvailability(
      userId: Long,
      fromWeekDay: Int,
      toWeekDay: Int,
      fromHour: Int,
      toHour: Int,
      status: AvailabilityStatus
  ): UserAvailability = {
    UserAvailability(
      id = userId * 100,
      userId = userId,
      fromWeekDay = fromWeekDay,
      toWeekDay = toWeekDay,
      fromHourInclusive = fromHour,
      toHourExclusive = toHour,
      availabilityStatus = status
    )
  }

  "PotentialMatchService" should {

    "return empty list when no availabilities exist" in {
      val testMatch = PotentialMatch(
        id = 1L,
        firstUserId = 1L,
        secondUserId = 2L,
        matchStartTime = Instant.EPOCH,
        status = PotentialMatchStatus.Potential,
        firstUserAvailabilityId = 1L,
        secondUserAvailabilityId = 2L,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
      )

      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        testMatch,
        Nil,
        Nil,
        madridZone,
        limaZone,
        calculationTimeThursday
      )
      result must be(Nil)
    }

    "return single match time with one overlapping availability" in {
      val availability1 = createTestAvailability(
        userId = 1L,
        fromWeekDay = 1,
        toWeekDay = 1,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )
      val availability2 = createTestAvailability(
        userId = 2L,
        fromWeekDay = 1,
        toWeekDay = 1,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val testMatch = PotentialMatch(
        id = 1L,
        firstUserId = 1L,
        secondUserId = 2L,
        matchStartTime = Instant.EPOCH,
        status = PotentialMatchStatus.Potential,
        firstUserAvailabilityId = 1L,
        secondUserAvailabilityId = 2L,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
      )

      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        testMatch,
        List(availability1),
        List(availability2),
        madridZone,
        limaZone,
        calculationTimeThursday
      )

      result must have size 1
      val matchTime = result.head

      matchTime.matchId mustBe(1L)
      LocalDateTime.from(matchTime.startTime.atZone(ZoneId.of("UTC"))).getDayOfWeek mustBe(DayOfWeek.MONDAY)
      matchTime.firstUserAvailability.userId mustBe(1L)
      matchTime.secondUserAvailability.userId mustBe(2L)
    }
    
  }
}

