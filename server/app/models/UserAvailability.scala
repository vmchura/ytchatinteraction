package models

import java.time.Instant

case class UserTimezone(
  userId: Long,
  timezone: String,
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now()
)

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
  case object Unavailable extends AvailabilityStatus
  case object MaybeAvailable extends AvailabilityStatus
  case object HighlyAvailable extends AvailabilityStatus
}
