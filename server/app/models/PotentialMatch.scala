package models

import java.time.Instant
import slick.jdbc.JdbcProfile

/**
 * Represents a potential match between two users based on their availability
 */
case class PotentialMatch(
  id: Long = 0L,
  firstUserId: Long,
  secondUserId: Long,
  matchStartTime: Instant,
  status: PotentialMatchStatus = PotentialMatchStatus.Potential,
  firstUserAvailabilityId: Long,
  secondUserAvailabilityId: Long,
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now()
)

/**
 * Status of a potential match
 */
sealed trait PotentialMatchStatus

object PotentialMatchStatus {
  case object Potential extends PotentialMatchStatus
  case object Declined extends PotentialMatchStatus
  case object Accepted extends PotentialMatchStatus
  
  def columnType(using profile: JdbcProfile): profile.api.BaseColumnType[PotentialMatchStatus] =
    import profile.api.*
    
    MappedColumnType.base[PotentialMatchStatus, String](
      _.toString,
      {
        case "Potential" => Potential
        case "Declined"  => Declined
        case "Accepted"  => Accepted
        case other        => throw new IllegalArgumentException(s"Unknown potential match status: $other")
      }
    )
}