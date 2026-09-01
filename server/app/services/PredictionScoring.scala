package services

/**
 * Pure scoring rules for the simplified prediction game.
 *
 * Group (GroupPosition):
 *   - exact position match: 2 points
 *   - correctly predicted player advances (top half of the group): 1 point
 *   - correctly predicted player eliminated (bottom half): 1 point
 *
 * Match (MatchScore):
 *   - correctly predicted winner (any score): 3 points
 *   - exact score match: 2 extra points
 */
object PredictionScoring {

  /** Scores allowed for a 1v1 Bo7 match prediction. */
  val matchScores: Seq[String] = Seq("4-0", "4-1", "4-2", "4-3", "3-4", "2-4", "1-4", "0-4")

  /**
   * @param ranking   ordered player keys, first element = predicted 1st place
   * @param positions result map, "1" -> player key of 1st place, etc.
   */
  def scoreGroup(ranking: Seq[String], positions: Map[String, String]): Int = {
    groupPlayerPoints(ranking, positions).map(_._2).sum
  }

  /**
   * Points earned by each player of the user's group prediction, in the same
   * order as `ranking`. A player can earn 2 points for the exact position plus
   * 1 point for a correctly predicted advance (top half) or elimination
   * (bottom half).
   */
  def groupPlayerPoints(ranking: Seq[String], positions: Map[String, String]): Seq[(String, Int)] = {
    val size = positions.size
    if (size == 0 || ranking.isEmpty) {
      return ranking.map(_ -> 0)
    }

    val advancingCount = size / 2
    val resultByPosition = (1 to size).map(i => positions.getOrElse(i.toString, "")).toVector
    val advancing = resultByPosition.take(advancingCount).toSet
    val eliminated = resultByPosition.drop(advancingCount).toSet

    ranking.zipWithIndex.map { case (player, i) =>
      var points = 0
      if (i < size && player == resultByPosition(i)) points += 2
      if (i < advancingCount && advancing.contains(player)) points += 1
      if (i >= advancingCount && i < size && eliminated.contains(player)) points += 1
      player -> points
    }
  }

  /**
   * @param predictedScore the score the user picked (e.g. "4-2")
   * @param actualScore    the real final score (e.g. "4-2")
   */
  def scoreMatch(predictedScore: String, actualScore: String): Int = {
    var points = 0
    val predictedWinner = winnerOf(predictedScore)
    val actualWinner = winnerOf(actualScore)
    if (predictedWinner.nonEmpty && predictedWinner == actualWinner) points += 3
    if (predictedScore == actualScore) points += 2
    points
  }

  /** "4-2" -> "player1", "2-4" -> "player2". Scores are always from player1's perspective. */
  def winnerOf(score: String): String = {
    score.split("-") match {
      case Array(a, b) =>
        (a.toIntOption, b.toIntOption) match {
          case (Some(pa), Some(pb)) if pa > pb => "player1"
          case (Some(pa), Some(pb)) if pb > pa => "player2"
          case _                               => ""
        }
      case _ => ""
    }
  }
}
