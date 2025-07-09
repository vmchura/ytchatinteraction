package models.component

import models.TournamentChallongeParticipant
import models.component.{TournamentComponent, UserComponent}
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery
import java.time.Instant

trait TournamentChallongeParticipantComponent extends TournamentComponent with UserComponent {
  protected val profile: JdbcProfile

  import profile.api.*

  class TournamentChallongeParticipantsTable(tag: Tag) extends Table[TournamentChallongeParticipant](tag, "tournament_challonge_participants") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def tournamentId = column[Long]("tournament_id")

    def userId = column[Long]("user_id")

    def challongeParticipantId = column[Long]("challonge_participant_id")

    def challongeTournamentId = column[Long]("challonge_tournament_id")

    def createdAt = column[Instant]("created_at")

    def updatedAt = column[Instant]("updated_at")

    def * = (id, tournamentId, userId, challongeParticipantId, challongeTournamentId, createdAt, updatedAt) <>
      ((TournamentChallongeParticipant.apply _).tupled, TournamentChallongeParticipant.unapply)

    // Foreign key constraints
    def fkTournament = foreignKey("fk_tournament_challonge_participants_tournament", tournamentId, tournamentsTable)(_.id)

    def fkUser = foreignKey("fk_tournament_challonge_participants_user", userId, usersTable)(_.userId)

    // Unique constraint to prevent duplicate mappings for the same tournament/user combination
    def uniqueTournamentUserChallonge = index("idx_tournament_challonge_participants_unique", (tournamentId, userId), unique = true)

    // Index for faster lookups by Challonge participant ID
    def idxChallongeParticipantId = index("idx_tournament_challonge_participants_challonge_id", challongeParticipantId, unique = false)

    // Index for faster lookups by Challonge tournament ID
    def idxChallongeTournamentId = index("idx_tournament_challonge_participants_challonge_tournament_id", challongeTournamentId, unique = false)
  }

  val tournamentChallongeParticipantsTable = TableQuery[TournamentChallongeParticipantsTable]
}
