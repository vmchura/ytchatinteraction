package models.viewmodels

import models.ChallongeMatch

/** Display model for tournament matches in the admin management view. Similar
  * to UserMatchInfo but designed for tournament-wide match display.
  */
case class TournamentMatchDisplay(
    matchId: Long,
    matchIdentifier: String, // e.g., "Round 1 - Match 3" or similar
    player1Name: String,
    player2Name: String,
    status: String, // "pending", "open", "complete"
    winnerName: Option[String],
    scheduledTime: Option[String],
    round: Option[Int] // Round number if available
)

object TournamentMatchDisplay {

  /** Creates a TournamentMatchDisplay from a ChallongeMatch and participant
    * names.
    *
    * @param challongeMatch
    *   The match data from Challonge API
    * @param participantNames
    *   Map of participant IDs to names
    * @return
    *   TournamentMatchDisplay for admin view
    */
  def fromChallongeMatch(
      challongeMatch: ChallongeMatch,
      participantNames: Map[Long, String]
  ): TournamentMatchDisplay = {
    val player1 =
      challongeMatch.player1Id.flatMap(participantNames.get).getOrElse("TBD")
    val player2 =
      challongeMatch.player2Id.flatMap(participantNames.get).getOrElse("TBD")

    TournamentMatchDisplay(
      matchId = challongeMatch.id,
      matchIdentifier = s"Match ${challongeMatch.id}",
      player1Name = player1,
      player2Name = player2,
      status = challongeMatch.state,
      winnerName = challongeMatch.winnerId.flatMap(participantNames.get),
      scheduledTime = challongeMatch.scheduledTime,
      round = None // Could be extracted from Challonge data if available
    )
  }
}

