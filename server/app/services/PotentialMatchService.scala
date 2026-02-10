package services

import models.{
  PotentialMatch,
  PotentialMatchStatus,
  UserAvailability,
  AvailabilityStatus
}
import models.repository.{PotentialMatchRepository, UserAvailabilityRepository}
import models.User
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import java.time.{
  DayOfWeek,
  Instant,
  LocalDateTime,
  ZonedDateTime,
  ZoneId,
  Duration
}
import java.time.format.DateTimeFormatter
import java.time.temporal.{
  ChronoField,
  ChronoUnit,
  TemporalAdjusters,
  WeekFields
}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable
import scala.math.Ordering.Implicits.infixOrderingOps

/** Result of potential match time calculation
  */
case class PotentialMatchTime(
    startTime: Instant,
    firstUserAvailability: UserAvailability,
    secondUserAvailability: UserAvailability
) {
  def toPotentialMatch(matchId: Long): PotentialMatch = {
    PotentialMatch(
      id = matchId,
      firstUserId = firstUserAvailability.userId,
      secondUserId = secondUserAvailability.userId,
      matchStartTime = startTime,
      status = PotentialMatchStatus.Potential,
      firstUserAvailabilityId = firstUserAvailability.id,
      secondUserAvailabilityId = secondUserAvailability.id,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
  }
}

/** Represents a time slot availability
  */
case class AvailabilitySlot(
    start: Instant,
    end: Instant
)
object AvailabilitySlot:
  private val MinimumOverlap: Duration = Duration.ofMinutes(45)

  def commonSlot(
      a: AvailabilitySlot,
      b: AvailabilitySlot
  ): Option[AvailabilitySlot] =
    val left: Instant = if a.start.isAfter(b.start) then a.start else b.start
    val right: Instant = if a.end.isBefore(b.end) then a.end else b.end

    val overlap: Duration = Duration.between(left, right)

    Option.when(overlap.compareTo(MinimumOverlap) > 0) {
      AvailabilitySlot(left, right)
    }

object PotentialMatchCalculator {

  /** Finds optimal match times between two users based on their weekly availability schedules.
    *
    * This method calculates potential match times by finding overlapping availability windows
    * between two users, converting their local availability times to UTC for comparison.
    *
    * Algorithm:
    * 1. Start searching from the day after calculationTime (UTC-based days)
    * 2. Search window: maximum 7 days (one week) into the future
    * 3. For each future day, check if both users have availability that overlaps
    * 4. Only consider overlaps of at least 45 minutes
    * 5. Rank matches by availability status priority:
    *    - Highest priority: Both users have HighlyAvailable status
    *    - Lower priority: Mixed or MaybeAvailable statuses
    * 6. If multiple valid matches exist on the same day, select the earliest one
    * 7. Return up to 2 optimal match times (the 2 best available slots)
    *
    * Time zone handling:
    * - User availabilities are defined in their local time zones
    * - All calculations are performed in UTC to ensure accurate overlap detection
    * - Days are determined based on UTC time (00:00-23:59 UTC)
    *
    * @param firstAvailabilities  Weekly availability ranges for user 1
    * @param secondAvailabilities Weekly availability ranges for user 2
    * @param firstTimeZoneID      IANA time zone ID for user 1 (e.g., "America/New_York")
    * @param secondTimeZoneID     IANA time zone ID for user 2 (e.g., "Europe/London")
    * @param calculationTime      Reference timestamp; search starts from the next UTC day
    * @return List of up to 2 optimal match times, sorted by priority and then by time
    */
  def findOptimalMatchTimes(
      firstAvailabilities: Seq[UserAvailability],
      secondAvailabilities: Seq[UserAvailability],
      firstTimeZoneID: String,
      secondTimeZoneID: String,
      calculationTime: Instant
  ): List[PotentialMatchTime] = ???

}

/** Service for calculating optimal match times between users based on their
  * availabilities
  */
@Singleton
class PotentialMatchService @Inject() (
    potentialMatchRepository: PotentialMatchRepository,
    userAvailabilityRepository: UserAvailabilityRepository
)(implicit ec: ExecutionContext, dbConfigProvider: DatabaseConfigProvider) {

  private val logger = Logger(getClass)
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  protected val profile = dbConfig.profile

  import dbConfig._
  import profile.api._

  /** Calculates optimal match times for a specific match ID
    * @param matchId - The ID of the potential match to calculate for
    * @param calculationTime - Current time when calculation is performed
    * @return Future list of optimal match times (max 2, sorted by proposed time)
    */
  def calculateOptimalMatchTimes(
      matchId: Long,
      calculationTime: Instant
  ): Future[List[PotentialMatchTime]] = {

    val calculation = db.run {
      for {
        matchOpt <- potentialMatchRepository.findByIdAction(matchId)
        result <- matchOpt match {
          case Some(matchRecord) =>
            for {
              firstUserAvailabilities <- userAvailabilityRepository
                .getAllAvailabilitiesByUserIdAction(matchRecord.firstUserId)
              secondUserAvailabilities <- userAvailabilityRepository
                .getAllAvailabilitiesByUserIdAction(matchRecord.secondUserId)
              firstUserTimeZoneOpt <- userAvailabilityRepository.getTimezoneAction(
                matchRecord.firstUserId
              )
              secondUserTimeZoneOpt <- userAvailabilityRepository.getTimezoneAction(
                matchRecord.secondUserId
              )
            } yield {
              firstUserTimeZoneOpt.zip(secondUserTimeZoneOpt).match {
                case Some((firstUserTimeZone, secondUserTimeZone)) =>
                  PotentialMatchCalculator.findOptimalMatchTimes(
                    firstUserAvailabilities,
                    secondUserAvailabilities,
                    firstUserTimeZone.timezone,
                    secondUserTimeZone.timezone,
                    calculationTime
                  )
                case None => List.empty
              }
            }
          case None => DBIO.successful(List.empty)
        }
      } yield result
    }

    calculation
  }

}
