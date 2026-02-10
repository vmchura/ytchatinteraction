package models

import java.time.Instant

case class UserTimezone(
    userId: Long,
    timezone: String,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
)

/** Represents a user's weekly availability time range in the database.
  *
  * This class models a recurring weekly schedule using half-open intervals:
  *   - Days: 1=Monday through 7=Sunday (inclusive range)
  *   - Hours: 0-23 inclusive start, exclusive end (e.g., 8 to 16 means
  *     8:00-15:59)
  *
  * Example: A worker available Monday-Friday, 8:00 AM to 4:00 PM fromWeekDay =
  * 1, toWeekDay = 5, fromHourInclusive = 8, toHourExclusive = 16
  *
  * @param id
  *   Auto-generated primary key
  * @param userId
  *   Foreign key to the user
  * @param fromWeekDay
  *   Start day (1=Monday, inclusive)
  * @param toWeekDay
  *   End day (1-7, inclusive)
  * @param fromHourInclusive
  *   Start hour (0-23, inclusive)
  * @param toHourExclusive
  *   End hour (1-24, exclusive)
  * @param availabilityStatus
  *   Level of availability (MaybeAvailable or HighlyAvailable)
  * @param createdAt
  *   Timestamp of record creation
  * @param updatedAt
  *   Timestamp of last update
  */
case class UserAvailability(
    id: Long = 0L,
    userId: Long,
    fromWeekDay: Int,
    toWeekDay: Int,
    fromHourInclusive: Int,
    toHourExclusive: Int,
    availabilityStatus: AvailabilityStatus,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
)

sealed trait AvailabilityStatus

object AvailabilityStatus {
  case object MaybeAvailable extends AvailabilityStatus
  case object HighlyAvailable extends AvailabilityStatus
}
