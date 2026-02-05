package services

import models.{PotentialMatch, PotentialMatchStatus, UserAvailability, AvailabilityStatus}
import models.repository.{PotentialMatchRepository, UserAvailabilityRepository}
import models.User
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import java.time.{DayOfWeek, Instant, LocalDateTime, ZonedDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import java.time.temporal.{ChronoField, ChronoUnit, TemporalAdjusters, WeekFields}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable
import scala.math.Ordering.Implicits.infixOrderingOps

/**
 * Result of potential match time calculation
 */
case class PotentialMatchTime(
  matchId: Long,
  firstUserId: Long,
  secondUserId: Long,
  proposedTime: LocalDateTime,
  firstUserAvailability: UserAvailability,
  secondUserAvailability: UserAvailability,
  dayOfWeek: DayOfWeek
) {
  def toPotentialMatch: PotentialMatch = {
    val instant = proposedTime.atZone(ZoneId.of("UTC")).toInstant
    PotentialMatch(
      id = matchId,
      firstUserId = firstUserId,
      secondUserId = secondUserId,
      matchStartTime = instant,
      status = PotentialMatchStatus.Potential,
      firstUserAvailabilityId = firstUserAvailability.id,
      secondUserAvailabilityId = secondUserAvailability.id,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
  }
}

/**
 * Represents a time slot availability
 */
case class AvailabilitySlot(
  availability: UserAvailability,
  start: Instant,
  end: Instant
)

/**
 * Represents a potential time slot between two users
 */
case class TimeSlot(
  firstAvailability: UserAvailability,
  secondAvailability: UserAvailability,
  startTime: Instant,
  endTime: Instant,
  firstPriority: AvailabilityStatus,
  secondPriority: AvailabilityStatus
) {
  def toPotentialMatchTime(matchId: Long): PotentialMatchTime = {
    PotentialMatchTime(
      matchId = matchId,
      firstUserId = firstAvailability.userId,
      secondUserId = secondAvailability.userId,
      proposedTime = startTime.atZone(ZoneId.of("UTC")).toLocalDateTime,
      firstUserAvailability = firstAvailability,
      secondUserAvailability = secondAvailability,
      dayOfWeek = startTime.atZone(ZoneId.of("UTC")).getDayOfWeek
    )
  }
}

/**
 * Service for calculating optimal match times between users based on their availabilities
 */
@Singleton
class PotentialMatchService @Inject()(
  potentialMatchRepository: PotentialMatchRepository,
  userAvailabilityRepository: UserAvailabilityRepository
)(implicit ec: ExecutionContext, dbConfigProvider: DatabaseConfigProvider) {
  
  private val logger = Logger(getClass)
  private val dbConfig = dbConfigProvider.get[slick.jdbc.JdbcProfile]
  import dbConfig.profile.api._
  
  /**
   * Calculates optimal match times for a specific match ID
   * @param matchId - The ID of the potential match to calculate for
   * @param calculationTime - Current time when calculation is performed
   * @return Future list of optimal match times (max 2, sorted by proposed time)
   */
  def calculateOptimalMatchTimes(matchId: Long, calculationTime: Instant): Future[List[PotentialMatchTime]] = {
    
    val calculation = db.run {
      for {
        matchOpt <- potentialMatchRepository.findByIdAction(matchId)
        result <- matchOpt match {
          case Some(matchRecord) => 
            for {
              firstUserAvailabilities <- userAvailabilityRepository.findByUserIdAction(matchRecord.firstUserId)
              secondUserAvailabilities <- userAvailabilityRepository.findByUserIdAction(matchRecord.secondUserId)
            } yield {
              findOptimalMatchTimes(matchRecord, firstUserAvailabilities, secondUserAvailabilities, calculationTime)
            }
          case None => DBIO.successful(List.empty)
        }
      } yield result
    }
    
    calculation.andThen { results =>
      logger.info(s"Calculated ${results.length} optimal match times for matchId $matchId")
      results
    }
  }
  
  /**
   * Internal method to calculate best match times based on user availabilities
   */
  private def findOptimalMatchTimes(
    matchRecord: PotentialMatch,
    firstAvailabilities: Seq[UserAvailability],
    secondAvailabilities: Seq[UserAvailability],
    calculationTime: Instant
  ): List[PotentialMatchTime] = {
    
    val calculationDate = calculationTime.atZone(ZoneId.of("UTC")).toLocalDate
    val startOfNextDay = calculationDate.plusDays(1).atStartOfDay.atZone(ZoneId.of("UTC")).toInstant
    
    val allSlots = findOverlappingSlots(firstAvailabilities, secondAvailabilities, startOfNextDay)
    
    // Prioritize: HIGHLY + HIGHLY > MAYBE + MAYBE
    implicit val slotOrdering: Ordering[TimeSlot] = Ordering.by { slot =>
      val priority = (slot.firstPriority, slot.secondPriority) match {
        case (AvailabilityStatus.HighlyAvailable, AvailabilityStatus.HighlyAvailable) => 0
        case (AvailabilityStatus.HighlyAvailable, AvailabilityStatus.MaybeAvailable) => 1
        case (AvailabilityStatus.MaybeAvailable, AvailabilityStatus.HighlyAvailable) => 1
        case (AvailabilityStatus.MaybeAvailable, AvailabilityStatus.MaybeAvailable) => 2
      }
      (priority, slot.startTime)
    }
    
    val sortedSlots = allSlots.sorted(slotOrdering)
    
    // Group by day and take earliest from each day
    val earliestByDay = mutable.Map[DayOfWeek, PotentialMatchTime]()
    
    sortedSlots.foreach { slot =>
      val dayOfWeek = slot.startTime.atZone(ZoneId.of("UTC")).getDayOfWeek
      if (!earliestByDay.contains(dayOfWeek)) {
        earliestByDay(dayOfWeek) = slot.toPotentialMatchTime(matchRecord.id)
      }
    }
    
    // Convert to list and take max 2, sorted by time
    implicit val timeOrdering: Ordering[PotentialMatchTime] = Ordering.by(_.proposedTime)
    val result = earliestByDay.values.toList
      .sorted(timeOrdering)
      .take(2)
    
    result
  }
  
  /**
   * Finds overlapping time slots between two users' availabilities
   */
  private def findOverlappingSlots(
    firstAvailabilities: Seq[UserAvailability],
    secondAvailabilities: Seq[UserAvailability],
    earliestTime: Instant
  ): List[TimeSlot] = {
    
    val slots = mutable.ListBuffer[TimeSlot]()
    
    for (firstAvail <- firstAvailabilities) {
      for (secondAvail <- secondAvailabilities) {
        val overlappingSlots = findOverlapBetweenAvailabilities(firstAvail, secondAvail, earliestTime)
        slots ++= overlappingSlots
      }
    }
    
    slots.toList
  }
  
  /**
   * Finds overlapping time slots between two specific availabilities
   */
  private def findOverlapBetweenAvailabilities(
    firstAvail: UserAvailability,
    secondAvail: UserAvailability,
    earliestTime: Instant
  ): List[TimeSlot] = {
    
    val slots = mutable.ListBuffer[TimeSlot]()
    
    // Convert availabilities to actual time slots for the week
    val firstSlots = availabilityToSlots(firstAvail, earliestTime)
    val secondSlots = availabilityToSlots(secondAvail, earliestTime)
    
    // Find overlapping slots
    for (firstSlot <- firstSlots) {
      for (secondSlot <- secondSlots) {
        val overlap = findOverlap(firstSlot, secondSlot)
        overlap.foreach { overlappingSlot =>
          slots += TimeSlot(
            firstAvailability = firstAvail,
            secondAvailability = secondAvail,
            startTime = overlappingSlot.start,
            endTime = overlappingSlot.end,
            firstPriority = firstAvail.availabilityStatus,
            secondPriority = secondAvail.availabilityStatus
          )
        }
      }
    }
    
    slots.toList
  }
  
  /**
   * Converts a UserAvailability to actual time slots for the current week
   */
  private def availabilityToSlots(availability: UserAvailability, fromTime: Instant): List[AvailabilitySlot] = {
    val slots = mutable.ListBuffer[AvailabilitySlot]()
    val startDate = fromTime.atZone(ZoneId.of("UTC")).toLocalDate
    
    // Handle multi-day availability
    var currentDay = availability.fromWeekDay
    while (currentDay <= availability.toWeekDay) {
      val dayOfWeek = DayOfWeek.of(currentDay)
      
      val hourStart = if (currentDay == availability.fromWeekDay) availability.fromHourInclusive else 0
      val hourEnd = if (currentDay == availability.toWeekDay) availability.toHourExclusive else 24
      
      if (hourStart < hourEnd) {
        val slotDate = if (currentDay == availability.fromWeekDay) startDate else startDate.plusDays(currentDay - availability.fromWeekDay)
        val startTime = slotDate.atTime(hourStart, 0).atZone(ZoneId.of("UTC")).toInstant
        val endTime = slotDate.atTime(hourEnd - 1, 0).atZone(ZoneId.of("UTC")).toInstant
        
        slots += AvailabilitySlot(
          availability = availability,
          start = startTime,
          end = endTime
        )
      }
      
      currentDay += 1
    }
    
    slots.toList
  }
  
  /**
   * Finds overlap between two time slots
   */
  private def findOverlap(slot1: AvailabilitySlot, slot2: AvailabilitySlot): Option[AvailabilitySlot] = {
    val start = if (slot1.start.isAfter(slot2.start)) slot1.start else slot2.start
    val end = if (slot1.end.isBefore(slot2.end)) slot1.end else slot2.end
    
    if (start.isBefore(end)) {
      Some(AvailabilitySlot(
        availability = slot1.availability, // Use first slot's availability as reference
        start = start,
        end = end
      ))
    } else {
      None
    }
  }
}