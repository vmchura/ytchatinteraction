package models.component

import models.{Tournament, TournamentStatus}
import slick.jdbc.JdbcProfile
import slick.ast.BaseTypedType

import java.time.Instant

trait TournamentComponent {
  protected val profile: JdbcProfile

  import profile.api.*

  given BaseTypedType[TournamentStatus] =
    MappedColumnType.base[TournamentStatus, String](
      {
        case TournamentStatus.RegistrationOpen   => "RegistrationOpen"
        case TournamentStatus.RegistrationClosed => "RegistrationClosed"
        case TournamentStatus.InProgress         => "InProgress"
        case TournamentStatus.Completed          => "Completed"
        case TournamentStatus.Cancelled          => "Cancelled"
      },
      {
        case "RegistrationOpen"   => TournamentStatus.RegistrationOpen
        case "RegistrationClosed" => TournamentStatus.RegistrationClosed
        case "InProgress"         => TournamentStatus.InProgress
        case "Completed"          => TournamentStatus.Completed
        case "Cancelled"          => TournamentStatus.Cancelled
        case s                    =>
          throw new IllegalArgumentException(s"Unknown tournament status: $s")
      }
    )

  class TournamentsTable(tag: Tag)
      extends Table[Tournament](tag, "tournaments") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def description = column[Option[String]]("description")
    def maxParticipants = column[Int]("max_participants")
    def registrationStartAt = column[Instant]("registration_start_at")
    def registrationEndAt = column[Instant]("registration_end_at")
    def tournamentStartAt = column[Option[Instant]]("tournament_start_at")
    def tournamentEndAt = column[Option[Instant]]("tournament_end_at")
    def challongeTournamentId = column[Option[Long]]("challonge_tournament_id")
    def challongeUrl = column[Option[String]]("challonge_url")
    def contentCreatorChannelId =
      column[Option[Long]]("content_creator_channel_id")
    def status = column[TournamentStatus]("status")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")
    def tournamentCode = column[String]("tournament_code")

    def * = (
      id,
      name,
      description,
      maxParticipants,
      registrationStartAt,
      registrationEndAt,
      tournamentCode,
      tournamentStartAt,
      tournamentEndAt,
      challongeTournamentId,
      challongeUrl,
      contentCreatorChannelId,
      status,
      createdAt,
      updatedAt
    ).mapTo[Tournament]
  }

  lazy val tournamentsTable = TableQuery[TournamentsTable]
}
