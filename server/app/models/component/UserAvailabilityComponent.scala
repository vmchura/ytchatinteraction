package models.component

import models.{UserAvailability, UserTimezone, AvailabilityStatus}
import slick.jdbc.JdbcProfile

import java.time.Instant

trait UserAvailabilityComponent extends UserComponent {
  protected val profile: JdbcProfile

  import profile.api._

  given BaseColumnType[AvailabilityStatus] = MappedColumnType.base[AvailabilityStatus, String](
    {
      case AvailabilityStatus.MaybeAvailable => "MAYBE_AVAILABLE"
      case AvailabilityStatus.HighlyAvailable => "HIGHLY_AVAILABLE"
    },
    {
      case "MAYBE_AVAILABLE" => AvailabilityStatus.MaybeAvailable
      case "HIGHLY_AVAILABLE" => AvailabilityStatus.HighlyAvailable
    }
  )

  class UserTimezonesTable(tag: Tag) extends Table[UserTimezone](tag, "user_timezones") {
    def userId = column[Long]("user_id", O.PrimaryKey)
    def timezone = column[String]("timezone")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")

    def userFk = foreignKey("fk_user_timezones_users", userId, usersTable)(_.userId, onDelete = ForeignKeyAction.Cascade)

    def * = (userId, timezone, createdAt, updatedAt).mapTo[UserTimezone]
  }

  class UserAvailabilityTable(tag: Tag) extends Table[UserAvailability](tag, "user_availability") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def fromWeekDay = column[Int]("from_week_day")
    def toWeekDay = column[Int]("to_week_day")
    def fromHourInclusive = column[Int]("from_hour_inclusive")
    def toHourExclusive = column[Int]("to_hour_exclusive")
    def availabilityStatus = column[AvailabilityStatus]("availability_status")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")

    def userFk = foreignKey("fk_user_availability_users", userId, usersTable)(_.userId, onDelete = ForeignKeyAction.Cascade)

    def * = (
      id,
      userId,
      fromWeekDay,
      toWeekDay,
      fromHourInclusive,
      toHourExclusive,
      availabilityStatus,
      createdAt,
      updatedAt
    ).mapTo[UserAvailability]
  }

  lazy val userTimezonesTable = TableQuery[UserTimezonesTable]
  lazy val userAvailabilityTable = TableQuery[UserAvailabilityTable]
}
