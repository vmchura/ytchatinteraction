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
  tournamentCode: String,
  tournamentStartAt: Option[Instant] = None,
  tournamentEndAt: Option[Instant] = None,
  challongeTournamentId: Option[Long] = None, // Nullable Challonge tournament ID
  challongeUrl: Option[String] = None, // Nullable Challonge url
  contentCreatorChannelId: Option[Long] = None, // Nullable Content Creator Channel ID
  status: TournamentStatus = TournamentStatus.RegistrationOpen,
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now()
)

sealed trait TournamentStatus

object TournamentStatus {
  case object RegistrationOpen extends TournamentStatus
  case object RegistrationClosed extends TournamentStatus
  case object InProgress extends TournamentStatus
  case object Completed extends TournamentStatus
  case object Cancelled extends TournamentStatus
}
