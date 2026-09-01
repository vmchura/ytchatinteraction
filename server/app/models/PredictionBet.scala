package models

import play.api.libs.json._
import java.time.Instant

/**
 * A user's saved prediction for a matchup.
 *
 * `ranking` is interpreted depending on the matchup's bet type:
 *  - GroupPosition: an ordered permutation of the player slot keys
 *    (e.g. Seq("player3", "player1", "player4", "player2")) meaning the user
 *    predicts player3 finishes 1st, player1 2nd, and so on.
 *  - MatchScore: a single-element sequence with the chosen score
 *    (e.g. Seq("4-2")).
 *
 * A user can have at most one prediction per matchup and can change it while
 * the matchup is still Open (not locked/scored).
 */
case class PredictionBet(
  id: Long = 0L,
  userId: Long,
  matchupId: Long,
  ranking: Seq[String],
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now()
)

object PredictionBet {
  implicit val predictionBetFormat: OFormat[PredictionBet] = Json.format[PredictionBet]
}
