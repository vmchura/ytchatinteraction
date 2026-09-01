package services

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PredictionScoringSpec extends AnyWordSpec with Matchers {

  "PredictionScoring.scoreGroup" should {

    "reproduce the example from the requirements (User A and User B)" in {
      // Actual result: [W, Y, Z, X]
      val positions = Map("1" -> "W", "2" -> "Y", "3" -> "Z", "4" -> "X")

      // User A prediction [W, X, Y, Z]:
      //   W exact position (2) + W advances (1) + Z eliminated (1) = 4
      PredictionScoring.scoreGroup(Seq("W", "X", "Y", "Z"), positions) mustBe 4

      // User B prediction [X, W, Z, Y]:
      //   W advances (1) + Z exact position (2) + Z eliminated (1) = 4
      PredictionScoring.scoreGroup(Seq("X", "W", "Z", "Y"), positions) mustBe 4
    }

    "award 2 points per exact position and 1 per advance/elimination on a perfect prediction" in {
      // Perfect prediction: 4 exact (4*2) + 2 advancing + 2 eliminated = 12
      val positions = Map("1" -> "A", "2" -> "B", "3" -> "C", "4" -> "D")
      PredictionScoring.scoreGroup(Seq("A", "B", "C", "D"), positions) mustBe 12
    }

    "award 0 points for the exact reverse order" in {
      val positions = Map("1" -> "A", "2" -> "B", "3" -> "C", "4" -> "D")
      PredictionScoring.scoreGroup(Seq("D", "C", "B", "A"), positions) mustBe 0
    }

    "award points for exact positions plus correctly predicted advance/elimination" in {
      // Result: [A, B, C, D] -> A and B advance, C and D eliminated.
      // Prediction [A, B, D, C]: A and B exact (4), A and B advance (2),
      // D and C eliminated (2) = 8
      val positions = Map("1" -> "A", "2" -> "B", "3" -> "C", "4" -> "D")
      PredictionScoring.scoreGroup(Seq("A", "B", "D", "C"), positions) mustBe 8
    }

    "award advance/elimination points only when the player is placed in the correct half" in {
      // Result: [A, B, C, D]. Prediction [C, D, A, B]:
      // no exact (0), advancing slots hold C and D (both actually eliminated, 0),
      // eliminated slots hold A and B (both actually advance, 0) => 0
      val positions = Map("1" -> "A", "2" -> "B", "3" -> "C", "4" -> "D")
      PredictionScoring.scoreGroup(Seq("C", "D", "A", "B"), positions) mustBe 0
    }

    "handle an empty or partial result defensively" in {
      PredictionScoring.scoreGroup(Seq("A", "B", "C", "D"), Map.empty) mustBe 0
      PredictionScoring.scoreGroup(Seq.empty, Map("1" -> "A", "2" -> "B", "3" -> "C", "4" -> "D")) mustBe 0
    }
  }

  "PredictionScoring.groupPlayerPoints" should {

    "attribute per-player points for the requirements example" in {
      // Result: [W, Y, Z, X]
      val positions = Map("1" -> "W", "2" -> "Y", "3" -> "Z", "4" -> "X")

      // User A [W, X, Y, Z]: W exact+advance (+3), X 0, Y 0, Z eliminated (+1)
      PredictionScoring.groupPlayerPoints(Seq("W", "X", "Y", "Z"), positions) mustBe
        Seq("W" -> 3, "X" -> 0, "Y" -> 0, "Z" -> 1)

      // User B [X, W, Z, Y]: X 0, W advance (+1), Z exact+eliminated (+3), Y 0
      PredictionScoring.groupPlayerPoints(Seq("X", "W", "Z", "Y"), positions) mustBe
        Seq("X" -> 0, "W" -> 1, "Z" -> 3, "Y" -> 0)
    }

    "award 3 points to every player of a perfect prediction" in {
      val positions = Map("1" -> "A", "2" -> "B", "3" -> "C", "4" -> "D")
      PredictionScoring.groupPlayerPoints(Seq("A", "B", "C", "D"), positions) mustBe
        Seq("A" -> 3, "B" -> 3, "C" -> 3, "D" -> 3)
    }

    "award 0 points to every player of a reversed prediction" in {
      val positions = Map("1" -> "A", "2" -> "B", "3" -> "C", "4" -> "D")
      PredictionScoring.groupPlayerPoints(Seq("D", "C", "B", "A"), positions) mustBe
        Seq("D" -> 0, "C" -> 0, "B" -> 0, "A" -> 0)
    }
  }

  "PredictionScoring.scoreMatch" should {

    "award 5 points for an exact score match (3 for winner + 2 extra)" in {
      PredictionScoring.scoreMatch("4-2", "4-2") mustBe 5
    }

    "award 3 points for the correct winner with a different score" in {
      PredictionScoring.scoreMatch("4-2", "4-1") mustBe 3
      PredictionScoring.scoreMatch("4-2", "3-4") mustBe 0 // wrong winner
    }

    "award 0 points for the wrong winner" in {
      PredictionScoring.scoreMatch("4-2", "2-4") mustBe 0
      PredictionScoring.scoreMatch("4-2", "1-4") mustBe 0
    }
  }

  "PredictionScoring.winnerOf" should {
    "detect player1 and player2 wins from the score" in {
      PredictionScoring.winnerOf("4-0") mustBe "player1"
      PredictionScoring.winnerOf("4-3") mustBe "player1"
      PredictionScoring.winnerOf("3-4") mustBe "player2"
      PredictionScoring.winnerOf("0-4") mustBe "player2"
    }
  }
}
