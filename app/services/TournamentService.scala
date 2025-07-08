package services

import models.{TournamentMatch, MatchStatus}
import models.repository.TournamentMatchRepository
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Service interface for TournamentMatch operations.
 */
trait TournamentService {

  /**
   * Creates a new tournament match.
   *
   * @param tournamentId The tournament ID
   * @param firstUserId The first user ID
   * @param secondUserId The second user ID
   * @return The created tournament match
   */
  def createMatch(matchID: String, tournamentId: String, firstUserId: Int, secondUserId: Int): Future[TournamentMatch]

  /**
   * Retrieves a tournament match by its ID.
   *
   * @param matchId The match ID
   * @return The tournament match if found, None otherwise
   */
  def getMatch(matchId: String): Future[Option[TournamentMatch]]

  /**
   * Retrieves all matches for a specific tournament.
   *
   * @param tournamentId The tournament ID
   * @return List of tournament matches
   */
  def getMatchesForTournament(tournamentId: String): Future[List[TournamentMatch]]

  /**
   * Retrieves all matches for a specific user.
   *
   * @param userId The user ID
   * @return List of tournament matches where the user is participating
   */
  def getMatchesForUser(userId: Long): Future[List[TournamentMatch]]

  /**
   * Updates the status of a tournament match.
   *
   * @param matchId The match ID
   * @param newStatus The new status
   * @return The updated tournament match if found, None otherwise
   */
  def updateMatchStatus(matchId: String, newStatus: MatchStatus): Future[Option[TournamentMatch]]

  /**
   * Starts a tournament match by changing its status to InProgress.
   *
   * @param matchId The match ID
   * @return The updated tournament match if found and was in Pending status, None otherwise
   */
  def startMatch(matchId: String): Future[Option[TournamentMatch]]

  /**
   * Completes a tournament match by changing its status to Completed.
   *
   * @param matchId The match ID
   * @return The updated tournament match if found and was in InProgress status, None otherwise
   */
  def completeMatch(matchId: String): Future[Option[TournamentMatch]]

  /**
   * Cancels a tournament match by changing its status to Cancelled.
   *
   * @param matchId The match ID
   * @return The updated tournament match if found, None otherwise
   */
  def cancelMatch(matchId: String): Future[Option[TournamentMatch]]

  /**
   * Deletes a tournament match.
   *
   * @param matchId The match ID
   * @return True if the match was deleted, false otherwise
   */
  def deleteMatch(matchId: String): Future[Boolean]

  /**
   * Retrieves matches by status.
   *
   * @param status The match status
   * @return List of tournament matches with the specified status
   */
  def getMatchesByStatus(status: MatchStatus): Future[List[TournamentMatch]]

  /**
   * Retrieves pending matches for a specific tournament.
   *
   * @param tournamentId The tournament ID
   * @return List of pending tournament matches
   */
  def getPendingMatches(tournamentId: String): Future[List[TournamentMatch]]

  /**
   * Retrieves active (InProgress) matches for a specific tournament.
   *
   * @param tournamentId The tournament ID
   * @return List of active tournament matches
   */
  def getActiveMatches(tournamentId: String): Future[List[TournamentMatch]]
}

/**
 * Implementation of TournamentService.
 *
 * @param tournamentMatchRepository The tournament match repository implementation.
 * @param ec The execution context.
 */
@Singleton
class TournamentServiceImpl @Inject() (
  tournamentMatchRepository: TournamentMatchRepository
)(implicit ec: ExecutionContext) extends TournamentService {

  /**
   * Creates a new tournament match.
   */
  override def createMatch(matchID: String, tournamentId: String, firstUserId: Int, secondUserId: Int): Future[TournamentMatch] = {
    val tournamentMatch = TournamentMatch(matchID, tournamentId, firstUserId, secondUserId)
    tournamentMatchRepository.create(tournamentMatch)
  }

  /**
   * Retrieves a tournament match by its ID.
   */
  override def getMatch(matchId: String): Future[Option[TournamentMatch]] = {
    tournamentMatchRepository.findById(matchId)
  }

  /**
   * Retrieves all matches for a specific tournament.
   */
  override def getMatchesForTournament(tournamentId: String): Future[List[TournamentMatch]] = {
    tournamentMatchRepository.findByTournamentId(tournamentId)
  }

  /**
   * Retrieves all matches for a specific user.
   */
  override def getMatchesForUser(userId: Long): Future[List[TournamentMatch]] = {
    tournamentMatchRepository.findByUserId(userId)
  }

  /**
   * Updates the status of a tournament match.
   */
  override def updateMatchStatus(matchId: String, newStatus: MatchStatus): Future[Option[TournamentMatch]] = {
    tournamentMatchRepository.updateStatus(matchId, newStatus)
  }

  /**
   * Starts a tournament match by changing its status to InProgress.
   */
  override def startMatch(matchId: String): Future[Option[TournamentMatch]] = {
    for {
      matchOpt <- tournamentMatchRepository.findById(matchId)
      result <- matchOpt match {
        case Some(matchTournament) if matchTournament.status == MatchStatus.Pending =>
          tournamentMatchRepository.updateStatus(matchId, MatchStatus.InProgress)
        case _ =>
          Future.successful(None)
      }
    } yield result
  }

  /**
   * Completes a tournament match by changing its status to Completed.
   */
  override def completeMatch(matchId: String): Future[Option[TournamentMatch]] = {
    for {
      matchOpt <- tournamentMatchRepository.findById(matchId)
      result <- matchOpt match {
        case Some(matchTournament) if matchTournament.status == MatchStatus.InProgress =>
          tournamentMatchRepository.updateStatus(matchId, MatchStatus.Completed)
        case _ =>
          Future.successful(None)
      }
    } yield result
  }

  /**
   * Cancels a tournament match by changing its status to Cancelled.
   */
  override def cancelMatch(matchId: String): Future[Option[TournamentMatch]] = {
    tournamentMatchRepository.updateStatus(matchId, MatchStatus.Cancelled)
  }

  /**
   * Deletes a tournament match.
   */
  override def deleteMatch(matchId: String): Future[Boolean] = {
    tournamentMatchRepository.delete(matchId)
  }

  /**
   * Retrieves matches by status.
   */
  override def getMatchesByStatus(status: MatchStatus): Future[List[TournamentMatch]] = {
    tournamentMatchRepository.findByStatus(status)
  }

  /**
   * Retrieves pending matches for a specific tournament.
   */
  override def getPendingMatches(tournamentId: String): Future[List[TournamentMatch]] = {
    tournamentMatchRepository.findByTournamentIdAndStatus(tournamentId, MatchStatus.Pending)
  }

  /**
   * Retrieves active (InProgress) matches for a specific tournament.
   */
  override def getActiveMatches(tournamentId: String): Future[List[TournamentMatch]] = {
    tournamentMatchRepository.findByTournamentIdAndStatus(tournamentId, MatchStatus.InProgress)
  }
}
