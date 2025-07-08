package models.component

import models.{TournamentRegistration, RegistrationStatus}
import models.component.{TournamentComponent, UserComponent}
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery
import java.time.Instant

trait TournamentRegistrationComponent extends TournamentComponent with UserComponent {
  protected val profile: JdbcProfile
  import profile.api.*

  // Custom column type for RegistrationStatus
  implicit val registrationStatusColumnType: BaseColumnType[RegistrationStatus] =
    MappedColumnType.base[RegistrationStatus, String](
      _.toString,
      {
        case "Registered" => RegistrationStatus.Registered
        case "Confirmed" => RegistrationStatus.Confirmed
        case "Withdrawn" => RegistrationStatus.Withdrawn
        case "DisqualifiedByAdmin" => RegistrationStatus.DisqualifiedByAdmin
        case other => throw new IllegalArgumentException(s"Unknown registration status: $other")
      }
    )



  class TournamentRegistrationsTable(tag: Tag) extends Table[TournamentRegistration](tag, "tournament_registrations") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def tournamentId = column[Long]("tournament_id")
    def userId = column[Long]("user_id")
    def registeredAt = column[Instant]("registered_at")
    def status = column[RegistrationStatus]("status")

    def * = (id, tournamentId, userId, registeredAt, status) <> 
            ((TournamentRegistration.apply _).tupled, TournamentRegistration.unapply)

    // Foreign key constraints
    def fkTournament = foreignKey("fk_tournament_registrations_tournament", tournamentId, tournamentsTable)(_.id)
    def fkUser = foreignKey("fk_tournament_registrations_user", userId, usersTable)(_.userId)

    // Unique constraint to prevent duplicate registrations
    def uniqueTournamentUser = index("idx_tournament_registrations_unique", (tournamentId, userId), unique = true)

  }

  val tournamentRegistrationsTable = TableQuery[TournamentRegistrationsTable]
}
