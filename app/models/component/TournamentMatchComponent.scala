package models.component

import models.{TournamentMatch, MatchStatus}
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery
import java.time.Instant

trait TournamentMatchComponent {
  protected val profile: JdbcProfile
  import profile.api.*

  // Custom column type for MatchStatus
  given BaseColumnType[MatchStatus] =
    MappedColumnType.base[MatchStatus, String](
      _.toString,
      {
        case "Pending" => MatchStatus.Pending
        case "InProgress" => MatchStatus.InProgress
        case "Completed" => MatchStatus.Completed
        case "Disputed" => MatchStatus.Disputed
        case "Cancelled" => MatchStatus.Cancelled
        case other => throw new IllegalArgumentException(s"Unknown match status: $other")
      }
    )

  // Custom column type for Instant
  given BaseColumnType[Instant] =
    MappedColumnType.base[Instant, java.sql.Timestamp](
      instant => java.sql.Timestamp.from(instant),
      timestamp => timestamp.toInstant
    )

  class TournamentMatchesTable(tag: Tag) extends Table[TournamentMatch](tag, "tournament_matches") {
    def matchId = column[Long]("match_id", O.PrimaryKey)
    def tournamentId = column[Long]("tournament_id")
    def firstUserId = column[Long]("first_user_id")
    def secondUserId = column[Long]("second_user_id")
    def createdAt = column[Instant]("created_at")
    def status = column[MatchStatus]("status")

    def * = (matchId, tournamentId, firstUserId, secondUserId, createdAt, status) <> ((TournamentMatch.apply _).tupled, TournamentMatch.unapply)

  }

  val tournamentMatchesTable = TableQuery[TournamentMatchesTable]
}
