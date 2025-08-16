package models

import java.time.Instant

/**
 * Represents a user's smurf/in-game alias associated with a match
 * 
 * @param id Sequential ID for the smurf record
 * @param matchId The tournament match ID this smurf was used in
 * @param tournamentId The tournament ID where this smurf was used
 * @param userId The user ID who used this smurf
 * @param smurf The in-game alias/smurf name used by the user
 * @param createdAt When this smurf record was created
 */
case class UserSmurf(
  id: Long,
  matchId: Long,
  tournamentId: Long,
  userId: Long,
  smurf: String,
  createdAt: Instant = Instant.now()
)
