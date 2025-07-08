package models.component

import models.{Tournament, TournamentStatus}
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery
import java.time.Instant

trait TournamentComponent {
  protected val profile: JdbcProfile
  import profile.api.*

  // Custom column type for TournamentStatus
  implicit val tournamentStatusColumnType: BaseColumnType[TournamentStatus] =
    MappedColumnType.base[TournamentStatus, String](
      _.toString,
      {
        case "RegistrationOpen" => TournamentStatus.RegistrationOpen
        case "RegistrationClosed" => TournamentStatus.RegistrationClosed
        case "InProgress" => TournamentStatus.InProgress
        case "Completed" => TournamentStatus.Completed
        case "Cancelled" => TournamentStatus.Cancelled
        case other => throw new IllegalArgumentException(s"Unknown tournament status: $other")
      }
    )

  // Custom column type for Instant
  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, java.sql.Timestamp](
      instant => java.sql.Timestamp.from(instant),
      timestamp => timestamp.toInstant
    )

  class TournamentsTable(tag: Tag) extends Table[Tournament](tag, "tournaments") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def description = column[Option[String]]("description")
    def maxParticipants = column[Int]("max_participants")
    def registrationStartAt = column[Instant]("registration_start_at")
    def registrationEndAt = column[Instant]("registration_end_at")
    def tournamentStartAt = column[Option[Instant]]("tournament_start_at")
    def tournamentEndAt = column[Option[Instant]]("tournament_end_at")
    def challongeTournamentId = column[Option[Long]]("challonge_tournament_id")
    def status = column[TournamentStatus]("status")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")

    def * = (id, name, description, maxParticipants, registrationStartAt, registrationEndAt, 
             tournamentStartAt, tournamentEndAt, challongeTournamentId, status, createdAt, updatedAt) <> 
             ((Tournament.apply _).tupled, Tournament.unapply)

    // Index for efficient queries
    def idxName = index("idx_tournaments_name", name)
    def idxStatus = index("idx_tournaments_status", status)
    def idxChallongeId = index("idx_tournaments_challonge_id", challongeTournamentId)
  }

  val tournamentsTable = TableQuery[TournamentsTable]
}
