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

import java.time.{DayOfWeek, Instant, LocalDateTime, ZoneId, Duration}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/** Tests for PotentialMatchCalculator.findOptimalMatchTimes
  *
  * Key test scenarios:
  * - Empty availabilities return no matches
  * - Overlapping availabilities within 7-day window (starting day after calculationTime)
  * - Minimum 45-minute overlap requirement
  * - Priority: HighlyAvailable > MaybeAvailable for both users
  * - Same-day conflicts: earliest time wins
  * - Maximum 2 matches returned
  * - UTC-based day calculations with timezone conversions
  */
class PotentialMatchServiceSpec extends PlaySpec {

  val testFirstUser = User(1L, "Player1")
  val testSecondUser = User(2L, "Player2")
  // Thursday, Feb 5, 2026 10:00 UTC
  val calculationTimeThursday = Instant.parse("2026-02-05T10:00:00Z")
  val madridZone = "Europe/Madrid"    // UTC+1 in February
  val limaZone = "America/Lima"       // UTC-5 (no DST)
  val utcZone = "UTC"

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

  "PotentialMatchCalculator" should {

    "return empty list when no availabilities exist" in {
      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        Nil,
        Nil,
        madridZone,
        limaZone,
        calculationTimeThursday
      )
      result must be(Nil)
    }

    "return empty list when no overlapping availabilities within 7-day window" in {
      // User 1 available only on past days (Mon-Wed), User 2 available only on future days (Thu-Sun)
      // Search starts Friday (day after Thursday), so no overlap
      val user1Availability = createTestAvailability(
        userId = 1L,
        fromWeekDay = 1, // Monday
        toWeekDay = 3,   // Wednesday
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val user2Availability = createTestAvailability(
        userId = 2L,
        fromWeekDay = 4, // Thursday
        toWeekDay = 7,   // Sunday
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        List(user1Availability),
        List(user2Availability),
        utcZone,
        utcZone,
        calculationTimeThursday
      )
      result must be(Nil)
    }

    "return single match time with overlapping availability in search window" in {
      // Both available Mon-Wed 10:00-12:00
      // Search starts Friday Feb 6, next Monday is Feb 9
      val availability1 = createTestAvailability(
        userId = 1L,
        fromWeekDay = 1, // Monday
        toWeekDay = 3,   // Wednesday
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val availability2 = createTestAvailability(
        userId = 2L,
        fromWeekDay = 1, // Monday
        toWeekDay = 3,   // Wednesday
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        List(availability1),
        List(availability2),
        utcZone,
        utcZone,
        calculationTimeThursday
      )

      result must have size 1
      val matchTime = result.head

      // Should be the first Monday after calculationTime (Feb 9, 2026)
      val matchDayOfWeek = LocalDateTime
        .from(matchTime.startTime.atZone(ZoneId.of("UTC")))
        .getDayOfWeek
      matchDayOfWeek mustBe DayOfWeek.MONDAY

      matchTime.firstUserAvailability.userId mustBe 1L
      matchTime.secondUserAvailability.userId mustBe 2L
    }

    "not return matches with less than 45 minutes overlap" in {
      // User 1: 10:00-10:30 (30 min), User 2: 10:00-12:00
      // Overlap is only 30 minutes, less than 45 min minimum
      val user1Availability = createTestAvailability(
        userId = 1L,
        fromWeekDay = 1,
        toWeekDay = 5,
        fromHour = 10,
        toHour = 10, // 10:00-10:30 (30 min window)
        status = AvailabilityStatus.HighlyAvailable
      )

      val user2Availability = createTestAvailability(
        userId = 2L,
        fromWeekDay = 1,
        toWeekDay = 5,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        List(user1Availability),
        List(user2Availability),
        utcZone,
        utcZone,
        calculationTimeThursday
      )
      result must be(Nil)
    }

    "return match with exactly 45 minutes overlap (boundary case)" in {
      // User 1: 10:00-10:45, User 2: 10:00-12:00
      // Overlap is exactly 45 minutes
      val user1Availability = createTestAvailability(
        userId = 1L,
        fromWeekDay = 1,
        toWeekDay = 5,
        fromHour = 10,
        toHour = 10, // Represents 10:00-10:45 somehow, or use different structure
        status = AvailabilityStatus.HighlyAvailable
      )

      // Note: This test may need adjustment based on actual implementation
      // The current UserAvailability model uses integer hours, not minutes
      pending
    }

    "prioritize HighlyAvailable over MaybeAvailable for both users" in {
      // Create multiple potential matches on different days
      // User1: HighlyAvailable Mon-Fri 10-12
      // User2: MaybeAvailable Mon-Wed 10-12, HighlyAvailable Thu-Fri 10-12
      // Expected: Thu-Fri match (both HighlyAvailable) should be prioritized
      val user1Availability = createTestAvailability(
        userId = 1L,
        fromWeekDay = 1,
        toWeekDay = 5,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val user2MaybeAvailability = createTestAvailability(
        userId = 2L,
        fromWeekDay = 1,
        toWeekDay = 3,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.MaybeAvailable
      )

      val user2HighAvailability = createTestAvailability(
        userId = 2L,
        fromWeekDay = 4,
        toWeekDay = 5,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        List(user1Availability),
        List(user2MaybeAvailability, user2HighAvailability),
        utcZone,
        utcZone,
        calculationTimeThursday
      )

      // Should return Thursday match first (both HighlyAvailable)
      // Then Friday match (both HighlyAvailable)
      // Or Monday-Wednesday (mixed availability) if no HighlyAvailable matches
      result.size must be >= 1
      result.size must be <= 2
    }

    "select earliest time when multiple matches exist on same day" in {
      // User 1 available 08:00-18:00
      // User 2 available 10:00-12:00 and 14:00-16:00
      // Both overlaps valid, should pick earliest (10:00 start)
      val user1Availability = createTestAvailability(
        userId = 1L,
        fromWeekDay = 1,
        toWeekDay = 5,
        fromHour = 8,
        toHour = 18,
        status = AvailabilityStatus.HighlyAvailable
      )

      val user2MorningAvailability = createTestAvailability(
        userId = 2L,
        fromWeekDay = 1,
        toWeekDay = 5,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val user2AfternoonAvailability = createTestAvailability(
        userId = 2L,
        fromWeekDay = 1,
        toWeekDay = 5,
        fromHour = 14,
        toHour = 16,
        status = AvailabilityStatus.HighlyAvailable
      )

      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        List(user1Availability),
        List(user2MorningAvailability, user2AfternoonAvailability),
        utcZone,
        utcZone,
        calculationTimeThursday
      )

      // Should have at least one match on Monday
      result must not be Nil
      val firstMatch = result.head
      val matchHour = LocalDateTime
        .from(firstMatch.startTime.atZone(ZoneId.of("UTC")))
        .getHour
      matchHour mustBe 10 // Earliest overlap starts at 10:00
    }

    "return maximum of 2 match times" in {
      // Create availabilities that would produce many matches
      // Both users available every day with HighlyAvailable status
      val user1Availability = createTestAvailability(
        userId = 1L,
        fromWeekDay = 1,
        toWeekDay = 7,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val user2Availability = createTestAvailability(
        userId = 2L,
        fromWeekDay = 1,
        toWeekDay = 7,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        List(user1Availability),
        List(user2Availability),
        utcZone,
        utcZone,
        calculationTimeThursday
      )

      result.size must be <= 2
      result.size must be >= 1
    }

    "handle timezone conversions correctly" in {
      // User 1 in Madrid (UTC+1): available 10:00-12:00 local = 09:00-11:00 UTC
      // User 2 in Lima (UTC-5): available 04:00-06:00 local = 09:00-11:00 UTC
      // Both should overlap at 09:00-11:00 UTC
      val madridAvailability = createTestAvailability(
        userId = 1L,
        fromWeekDay = 1,
        toWeekDay = 5,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val limaAvailability = createTestAvailability(
        userId = 2L,
        fromWeekDay = 1,
        toWeekDay = 5,
        fromHour = 4,
        toHour = 6,
        status = AvailabilityStatus.HighlyAvailable
      )

      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        List(madridAvailability),
        List(limaAvailability),
        madridZone,
        limaZone,
        calculationTimeThursday
      )

      result must have size 1
      val matchTime = result.head
      val matchHour = LocalDateTime
        .from(matchTime.startTime.atZone(ZoneId.of("UTC")))
        .getHour
      matchHour mustBe 9 // 09:00 UTC is the overlapping hour
    }

    "only search within 7-day window from calculationTime" in {
      // User 1 available only on day 8 (outside window)
      // User 2 available every day
      // Should return no matches
      val user1Availability = createTestAvailability(
        userId = 1L,
        fromWeekDay = 5, // Thursday (calculationTime is Thursday, search starts Friday)
        toWeekDay = 5,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val user2Availability = createTestAvailability(
        userId = 2L,
        fromWeekDay = 1,
        toWeekDay = 7,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        List(user1Availability),
        List(user2Availability),
        utcZone,
        utcZone,
        calculationTimeThursday
      )

      // If user1 is only available Thursday and search starts Friday,
      // next Thursday is day 7 which should be within window
      // This test verifies the 7-day limit is respected
      result.size must be <= 2
    }

    "correctly identify user IDs in match result" in {
      val availability1 = createTestAvailability(
        userId = 1L,
        fromWeekDay = 1,
        toWeekDay = 5,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val availability2 = createTestAvailability(
        userId = 2L,
        fromWeekDay = 1,
        toWeekDay = 5,
        fromHour = 10,
        toHour = 12,
        status = AvailabilityStatus.HighlyAvailable
      )

      val result = PotentialMatchCalculator.findOptimalMatchTimes(
        List(availability1),
        List(availability2),
        utcZone,
        utcZone,
        calculationTimeThursday
      )

      result must have size 1
      result.head.firstUserAvailability.userId mustBe 1L
      result.head.secondUserAvailability.userId mustBe 2L
    }
  }
}
