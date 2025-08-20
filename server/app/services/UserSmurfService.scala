package services

import evolutioncomplete.ParticipantShared
import models.UserSmurf
import models.repository.UserSmurfRepository
import play.api.Logging

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class UserSmurfService @Inject()(
  userSmurfRepository: UserSmurfRepository
)(implicit ec: ExecutionContext) extends Logging {

  def recordMatchSmurfs(
    matchId: Long,
    tournamentId: Long,
    firstParticipant: ParticipantShared,
    secondParticipant: ParticipantShared
  ): Future[Seq[UserSmurf]] = {
    
    val now = Instant.now()
    val smurfs = firstParticipant.smurfs.toList.map(s => UserSmurf(0L, matchId, tournamentId, firstParticipant.userID, s, now)) ++
      secondParticipant.smurfs.toList.map(s => UserSmurf(0L, matchId, tournamentId, secondParticipant.userID, s, now))
    

    userSmurfRepository.createBatch(smurfs)
  }

  /**
   * Gets all smurfs used by a user in a specific tournament.
   */
  def getUserSmurfsInTournament(userId: Long, tournamentId: Long): Future[List[UserSmurf]] = {
    userSmurfRepository.findByTournamentIdAndUserId(tournamentId, userId)
  }

  /**
   * Gets the unique smurf names used by a user in a tournament.
   */
  def getUniqueUserSmurfsInTournament(userId: Long, tournamentId: Long): Future[List[String]] = {
    userSmurfRepository.getUniqueSmurfsByTournamentAndUser(tournamentId, userId)
  }

  /**
   * Gets all smurfs used in a specific match.
   */
  def getMatchSmurfs(matchId: Long): Future[List[UserSmurf]] = {
    userSmurfRepository.findByMatchId(matchId)
  }

  /**
   * Gets the smurf used by a specific user in a specific match.
   */
  def getUserSmurfInMatch(matchId: Long, userId: Long): Future[Option[UserSmurf]] = {
    userSmurfRepository.findByMatchIdAndUserId(matchId, userId)
  }

  /**
   * Gets all unique smurfs used in a tournament.
   */
  def getAllTournamentSmurfs(tournamentId: Long): Future[List[String]] = {
    userSmurfRepository.getUniqueSmurfsByTournament(tournamentId)
  }

  /**
   * Gets all smurf records for a tournament.
   */
  def getTournamentSmurfRecords(tournamentId: Long): Future[List[UserSmurf]] = {
    userSmurfRepository.findByTournamentId(tournamentId)
  }

  /**
   * Gets all smurf records for a user across all tournaments.
   */
  def getUserSmurfs(userId: Long): Future[List[UserSmurf]] = {
    userSmurfRepository.findByUserId(userId)
  }

  /**
   * Deletes all smurf records for a match (useful for match resets).
   */
  def deleteMatchSmurfs(matchId: Long): Future[Int] = {
    logger.info(s"Deleting all smurf records for match $matchId")
    userSmurfRepository.deleteByMatchId(matchId)
  }

  /**
   * Gets match statistics showing how many smurfs have been recorded.
   */
  def getMatchSmurfCount(matchId: Long): Future[Int] = {
    userSmurfRepository.countByMatchId(matchId)
  }

  /**
   * Gets tournament statistics showing how many smurf records exist.
   */
  def getTournamentSmurfCount(tournamentId: Long): Future[Int] = {
    userSmurfRepository.countByTournamentId(tournamentId)
  }

  /**
   * Checks if smurfs have already been recorded for a match.
   */
  def hasMatchSmurfsRecorded(matchId: Long): Future[Boolean] = {
    getMatchSmurfCount(matchId).map(_ > 0)
  }
}
