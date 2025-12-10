package services

import evolutioncomplete.ParticipantShared
import models._
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
  ): Future[Seq[TournamentUserSmurf]] = {
    
    val now = Instant.now()
    val smurfs = firstParticipant.smurfs.toList.map(s => TournamentUserSmurf(0L, matchId, tournamentId, firstParticipant.userID, s, now)) ++
      secondParticipant.smurfs.toList.map(s => TournamentUserSmurf(0L, matchId, tournamentId, secondParticipant.userID, s, now))
    

    userSmurfRepository.createBatch(smurfs.map(_.toUserSmurf)).map(_.flatten)
  }

  /**
   * Gets all smurfs used by a user in a specific tournament.
   */
  def getUserSmurfsInTournament(userId: Long, tournamentId: Long): Future[List[TournamentUserSmurf]] = {
    userSmurfRepository.findByTournamentIdAndUserId(tournamentId, userId).map(_.flatten)
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
  def getMatchSmurfs(matchId: Long): Future[List[TournamentUserSmurf]] = {
    userSmurfRepository.findByMatchId(matchId).map(_.flatten)
  }

  /**
   * Gets the smurf used by a specific user in a specific match.
   */
  def getUserSmurfInMatch(matchId: Long, userId: Long): Future[Option[TournamentUserSmurf]] = {
    userSmurfRepository.findByMatchIdAndUserId(matchId, userId).map{
      case Some(us) => us
      case None => None
    }
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
  def getTournamentSmurfRecords(tournamentId: Long): Future[List[TournamentUserSmurf]] = {
    userSmurfRepository.findByTournamentId(tournamentId).map(_.flatten)
  }

  /**
   * Gets all smurf records for a user across all tournaments.
   */
  def getUserSmurfs(userId: Long): Future[List[TournamentUserSmurf]] = {
    userSmurfRepository.findByUserId(userId).map(_.flatten)
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
