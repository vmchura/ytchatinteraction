package models.component

import evolutioncomplete.WinnerShared
import models.{MatchStatus, TournamentMatch}
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

import java.time.Instant

trait TournamentMatchComponent extends MatchStatusColumnComponent{
  protected val profile: JdbcProfile
  import profile.api.*

  // Custom column type for Instant
  given BaseColumnType[Instant] =
    MappedColumnType.base[Instant, java.sql.Timestamp](
      instant => java.sql.Timestamp.from(instant),
      timestamp => timestamp.toInstant
    )

  given BaseColumnType[WinnerShared] =
    MappedColumnType.base[WinnerShared, String](_.toString, WinnerShared.valueOf)

  class TournamentMatchesTable(tag: Tag) extends Table[TournamentMatch](tag, "tournament_matches") {
    def matchId = column[Long]("match_id", O.PrimaryKey)
    def tournamentId = column[Long]("tournament_id")
    def firstUserId = column[Long]("first_user_id")
    def secondUserId = column[Long]("second_user_id")
    def winnerUserId = column[Option[Long]]("winner_user_id")
    def createdAt = column[Instant]("created_at")
    def status = column[MatchStatus]("status")
    def winner_description = column[WinnerShared]("winner_description")
    def * = (matchId, tournamentId, firstUserId, secondUserId, winnerUserId, status, winner_description, createdAt) <> ((TournamentMatch.apply _).tupled, TournamentMatch.unapply)

  }

  val tournamentMatchesTable = TableQuery[TournamentMatchesTable]
}
