package models.component

import models.UserSmurf
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery
import java.time.Instant

trait UserSmurfComponent {
  protected val profile: JdbcProfile
  import profile.api.*

  // Custom column type for Instant
  given BaseColumnType[Instant] =
    MappedColumnType.base[Instant, java.sql.Timestamp](
      instant => java.sql.Timestamp.from(instant),
      timestamp => timestamp.toInstant
    )

  class UserSmurfsTable(tag: Tag) extends Table[UserSmurf](tag, "user_smurfs") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def matchId = column[Long]("match_id")
    def tournamentId = column[Long]("tournament_id")
    def userId = column[Long]("user_id")
    def smurf = column[String]("smurf")
    def createdAt = column[Instant]("created_at")

    def * = (id, matchId, tournamentId, userId, smurf, createdAt) <> ((UserSmurf.apply _).tupled, UserSmurf.unapply)

    // Note: Foreign key constraints are defined in the database evolution script
  }

  val userSmurfsTable = TableQuery[UserSmurfsTable]
}
