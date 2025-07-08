package services

import models.{Tournament, TournamentMatch, TournamentRegistration, TournamentStatus, MatchStatus, RegistrationStatus, User}
import models.repository.{TournamentRepository, TournamentMatchRepository, TournamentRegistrationRepository}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

/**
 * Service interface for Tournament operations including tournaments, registrations, and matches.
 */
trait TournamentService {

  // Tournament management methods
  /**
   * Creates a new tournament.
   *
   * @param tournament The tournament to create
   * @return The created tournament
   */
  def createTournament(tournament: Tournament): Future[Tournament]

  /**
   * Retrieves a tournament by its ID.
   *
   * @param id The tournament ID
   * @return The tournament if found, None otherwise
   */
  def getTournament(id: Long): Future[Option[Tournament]]

  /**
   * Retrieves tournaments by status.
   *
   * @param status The tournament status
   * @return List of tournaments with the specified status
   */
  def getTournamentsByStatus(status: TournamentStatus): Future[List[Tournament]]

  /**
   * Retrieves tournaments that are open for registration.
   *
   * @return List of tournaments open for registration
   */
  def getOpenTournaments: Future[List[Tournament]]

  /**
   * Updates a tournament.
   *
   * @param tournament The tournament to update
   * @return The updated tournament if found, None otherwise
   */
  def updateTournament(tournament: Tournament): Future[Option[Tournament]]

  /**
   * Updates tournament status.
   *
   * @param id The tournament ID
   * @param newStatus The new status
   * @return The updated tournament if found, None otherwise
   */
  def updateTournamentStatus(id: Long, newStatus: TournamentStatus): Future[Option[Tournament]]

  /**
   * Sets the Challonge tournament ID for a tournament.
   *
   * @param id The tournament ID
   * @param challongeTournamentId The Challonge tournament ID
   * @return The updated tournament if found, None otherwise
   */
  def setChallongeTournamentId(id: Long, challongeTournamentId: Long): Future[Option[Tournament]]

  /**
   * Closes registration for tournaments whose registration period has ended.
   *
   * @return List of tournaments whose registration was closed
   */
  def closeExpiredRegistrations(): Future[List[Tournament]]

  // Registration management methods
  /**
   * Registers a user for a tournament.
   *
   * @param tournamentId The tournament ID
   * @param userId The user ID
   * @return The created registration or an error
   */
  def registerUser(tournamentId: Long, userId: Long): Future[Either[String, TournamentRegistration]]

  /**
   * Withdraws a user's registration from a tournament.
   *
   * @param tournamentId The tournament ID
   * @param userId The user ID
   * @return True if withdrawal was successful, false otherwise
   */
  def withdrawRegistration(tournamentId: Long, userId: Long): Future[Boolean]

  /**
   * Retrieves all registrations for a tournament.
   *
   * @param tournamentId The tournament ID
   * @return List of registrations
   */
  def getTournamentRegistrations(tournamentId: Long): Future[List[TournamentRegistration]]

  /**
   * Retrieves all registrations with user details for a tournament.
   *
   * @param tournamentId The tournament ID
   * @return List of registrations with user details
   */
  def getTournamentRegistrationsWithUsers(tournamentId: Long): Future[List[(TournamentRegistration, User)]]

  /**
   * Retrieves active registrations for a tournament.
   *
   * @param tournamentId The tournament ID
   * @return List of active registrations
   */
  def getActiveRegistrations(tournamentId: Long): Future[List[TournamentRegistration]]

  /**
   * Checks if a user is registered for a tournament.
   *
   * @param tournamentId The tournament ID
   * @param userId The user ID
   * @return True if user is registered, false otherwise
   */
  def isUserRegistered(tournamentId: Long, userId: Long): Future[Boolean]

  /**
   * Gets the registration count for a tournament.
   *
   * @param tournamentId The tournament ID
   * @return Number of active registrations
   */
  def getRegistrationCount(tournamentId: Long): Future[Int]

  // Match management methods (existing functionality)

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
 * @param tournamentRepository The tournament repository implementation.
 * @param tournamentMatchRepository The tournament match repository implementation.
 * @param tournamentRegistrationRepository The tournament registration repository implementation.
 * @param ec The execution context.
 */
@Singleton
class TournamentServiceImpl @Inject() (
  tournamentRepository: TournamentRepository,
  tournamentMatchRepository: TournamentMatchRepository,
  tournamentRegistrationRepository: TournamentRegistrationRepository
)(implicit ec: ExecutionContext) extends TournamentService {

  // Tournament management methods
  /**
   * Creates a new tournament.
   */
  override def createTournament(tournament: Tournament): Future[Tournament] = {
    tournamentRepository.create(tournament)
  }

  /**
   * Retrieves a tournament by its ID.
   */
  override def getTournament(id: Long): Future[Option[Tournament]] = {
    tournamentRepository.findById(id)
  }

  /**
   * Retrieves tournaments by status.
   */
  override def getTournamentsByStatus(status: TournamentStatus): Future[List[Tournament]] = {
    tournamentRepository.findByStatus(status)
  }

  /**
   * Retrieves tournaments that are open for registration.
   */
  override def getOpenTournaments: Future[List[Tournament]] = {
    tournamentRepository.findOpenForRegistration()
  }

  /**
   * Updates a tournament.
   */
  override def updateTournament(tournament: Tournament): Future[Option[Tournament]] = {
    tournamentRepository.update(tournament)
  }

  /**
   * Updates tournament status.
   */
  override def updateTournamentStatus(id: Long, newStatus: TournamentStatus): Future[Option[Tournament]] = {
    tournamentRepository.updateStatus(id, newStatus)
  }

  /**
   * Sets the Challonge tournament ID for a tournament.
   */
  override def setChallongeTournamentId(id: Long, challongeTournamentId: Long): Future[Option[Tournament]] = {
    tournamentRepository.updateChallongeId(id, challongeTournamentId)
  }

  /**
   * Closes registration for tournaments whose registration period has ended.
   */
  override def closeExpiredRegistrations(): Future[List[Tournament]] = {
    for {
      expiredTournaments <- tournamentRepository.findRegistrationEnded()
      updatedTournaments <- Future.sequence(expiredTournaments.map { tournament =>
        tournamentRepository.updateStatus(tournament.id, TournamentStatus.RegistrationClosed)
      })
    } yield updatedTournaments.flatten
  }

  // Registration management methods
  /**
   * Registers a user for a tournament.
   */
  override def registerUser(tournamentId: Long, userId: Long): Future[Either[String, TournamentRegistration]] = {
    for {
      tournamentOpt <- tournamentRepository.findById(tournamentId)
      result <- tournamentOpt match {
        case None => Future.successful(Left("Tournament not found"))
        case Some(tournament) =>
          val now = Instant.now()
          if (tournament.status != TournamentStatus.RegistrationOpen) {
            Future.successful(Left("Registration is not open for this tournament"))
          } else if (now.isBefore(tournament.registrationStartAt)) {
            Future.successful(Left("Registration has not started yet"))
          } else if (now.isAfter(tournament.registrationEndAt)) {
            Future.successful(Left("Registration has ended"))
          } else {
            for {
              isAlreadyRegistered <- tournamentRegistrationRepository.isUserRegistered(tournamentId, userId)
              result <- if (isAlreadyRegistered) {
                Future.successful(Left("User is already registered for this tournament"))
              } else {
                for {
                  registrationCount <- tournamentRegistrationRepository.countActiveRegistrations(tournamentId)
                  finalResult <- if (registrationCount >= tournament.maxParticipants) {
                    Future.successful(Left("Tournament is full"))
                  } else {
                    val registration = TournamentRegistration(
                      tournamentId = tournamentId,
                      userId = userId
                    )
                    tournamentRegistrationRepository.create(registration).map(Right(_))
                  }
                } yield finalResult
              }
            } yield result
          }
      }
    } yield result
  }

  /**
   * Withdraws a user's registration from a tournament.
   */
  override def withdrawRegistration(tournamentId: Long, userId: Long): Future[Boolean] = {
    for {
      registrationOpt <- tournamentRegistrationRepository.findByTournamentAndUser(tournamentId, userId)
      result <- registrationOpt match {
        case None => Future.successful(false)
        case Some(registration) if registration.status == RegistrationStatus.Withdrawn =>
          Future.successful(false) // Already withdrawn
        case Some(registration) =>
          tournamentRegistrationRepository.updateStatus(registration.id, RegistrationStatus.Withdrawn).map(_.isDefined)
      }
    } yield result
  }

  /**
   * Retrieves all registrations for a tournament.
   */
  override def getTournamentRegistrations(tournamentId: Long): Future[List[TournamentRegistration]] = {
    tournamentRegistrationRepository.findByTournamentId(tournamentId)
  }

  /**
   * Retrieves all registrations with user details for a tournament.
   */
  override def getTournamentRegistrationsWithUsers(tournamentId: Long): Future[List[(TournamentRegistration, User)]] = {
    tournamentRegistrationRepository.findWithUsersByTournamentId(tournamentId)
  }

  /**
   * Retrieves active registrations for a tournament.
   */
  override def getActiveRegistrations(tournamentId: Long): Future[List[TournamentRegistration]] = {
    tournamentRegistrationRepository.findActiveRegistrations(tournamentId)
  }

  /**
   * Checks if a user is registered for a tournament.
   */
  override def isUserRegistered(tournamentId: Long, userId: Long): Future[Boolean] = {
    tournamentRegistrationRepository.isUserRegistered(tournamentId, userId)
  }

  /**
   * Gets the registration count for a tournament.
   */
  override def getRegistrationCount(tournamentId: Long): Future[Int] = {
    tournamentRegistrationRepository.countActiveRegistrations(tournamentId)
  }

  // Match management methods (existing functionality)

  // Match management methods (existing functionality)
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
