package models

import java.time.Instant

/**
 * Represents a tournament
 */
case class Tournament(
  id: Long = 0L, // Sequential ID, will be auto-generated
  name: String,
  description: Option[String] = None,
  maxParticipants: Int,
  registrationStartAt: Instant,
  registrationEndAt: Instant,
  tournamentStartAt: Option[Instant] = None,
  tournamentEndAt: Option[Instant] = None,
  challongeTournamentId: Option[Long] = None, // Nullable Challonge tournament ID
  status: TournamentStatus = TournamentStatus.RegistrationOpen,
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now()
)

/**
 * Status of a tournament
 */
sealed trait TournamentStatus

object TournamentStatus {
  case object RegistrationOpen extends TournamentStatus
  case object RegistrationClosed extends TournamentStatus
  case object InProgress extends TournamentStatus
  case object Completed extends TournamentStatus
  case object Cancelled extends TournamentStatus
}
