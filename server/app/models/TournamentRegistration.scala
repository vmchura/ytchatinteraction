package models

import java.time.Instant

/**
 * Represents a user's registration to a tournament
 */
case class TournamentRegistration(
  id: Long = 0L, // Sequential ID, will be auto-generated
  tournamentId: Long,
  userId: Long,
  registeredAt: Instant = Instant.now(),
  status: RegistrationStatus = RegistrationStatus.Registered
)

/**
 * Status of a tournament registration
 */
sealed trait RegistrationStatus

object RegistrationStatus {
  case object Registered extends RegistrationStatus
  case object Confirmed extends RegistrationStatus
  case object Withdrawn extends RegistrationStatus
  case object DisqualifiedByAdmin extends RegistrationStatus
}
