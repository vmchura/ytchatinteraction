package models.component

import models.{PotentialMatch, PotentialMatchStatus}
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

import java.time.Instant

trait PotentialMatchComponent {
  protected val profile: JdbcProfile
  import profile.api.*

  // Custom column type for Instant
  given BaseColumnType[Instant] =
    MappedColumnType.base[Instant, java.sql.Timestamp](
      instant => java.sql.Timestamp.from(instant),
      timestamp => timestamp.toInstant
    )

  // Custom column type for PotentialMatchStatus
  given BaseColumnType[PotentialMatchStatus] =
    MappedColumnType.base[PotentialMatchStatus, String](
      _.toString,
      {
        case "Potential" => PotentialMatchStatus.Potential
        case "Declined"  => PotentialMatchStatus.Declined
        case "Accepted"  => PotentialMatchStatus.Accepted
        case other        => throw new IllegalArgumentException(s"Unknown potential match status: $other")
      }
    )

  class PotentialMatchesTable(tag: Tag) extends Table[PotentialMatch](tag, "potential_matches") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def firstUserId = column[Long]("first_user_id")
    def secondUserId = column[Long]("second_user_id")
    def matchStartTime = column[Instant]("match_start_time")
    def status = column[PotentialMatchStatus]("status")
    def firstUserAvailabilityId = column[Long]("first_user_availability_id")
    def secondUserAvailabilityId = column[Long]("second_user_availability_id")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")
    def * = (id, firstUserId, secondUserId, matchStartTime, status, firstUserAvailabilityId, secondUserAvailabilityId, createdAt, updatedAt) <> ((PotentialMatch.apply _).tupled, PotentialMatch.unapply)
  }

  val potentialMatchesTable = TableQuery[PotentialMatchesTable]
}