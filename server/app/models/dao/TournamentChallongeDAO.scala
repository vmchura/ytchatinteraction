package models.dao

import models.{Tournament, TournamentChallongeParticipant, TournamentRegistration, User}
import models.repository.{TournamentChallongeParticipantRepository, TournamentRegistrationRepository, TournamentRepository, UserRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

/**
 * Data Access Object for tournament Challonge operations.
 * Provides higher-level operations that combine multiple repositories.
 */
trait TournamentChallongeDAO {

  /**
   * Creates a Challonge participant mapping for a user in a tournament
   */
  def createChallongeParticipantMapping(
                                         tournamentId: Long,
                                         userId: Long,
                                         challongeParticipantId: Long,
                                         challongeTournamentId: Long
                                       ): Future[TournamentChallongeParticipant]

  /**
   * Gets the Challonge participant ID for a user in a tournament
   */
  def getChallongeParticipantId(tournamentId: Long, userId: Long): Future[Option[Long]]

  /**
   * Gets all Challonge participant mappings for a tournament
   */
  def getTournamentChallongeParticipants(tournamentId: Long): Future[List[TournamentChallongeParticipant]]

  /**
   * Gets tournament and user information for a Challonge participant ID
   */
  def getTournamentUserByChallongeParticipantId(challongeParticipantId: Long): Future[Option[(Tournament, User)]]

  /**
   * Gets all registered users for a tournament with their Challonge participant IDs (if they exist)
   */
  def getTournamentParticipantsWithChallongeIds(tournamentId: Long): Future[List[(User, Option[Long])]]

  /**
   * Checks if a user has a Challonge participant ID for a tournament
   */
  def hasChallongeParticipantId(tournamentId: Long, userId: Long): Future[Boolean]

  /**
   * Updates the Challonge participant ID for a user in a tournament
   */
  def updateChallongeParticipantId(
                                    tournamentId: Long,
                                    userId: Long,
                                    newChallongeParticipantId: Long
                                  ): Future[Option[TournamentChallongeParticipant]]

  /**
   * Removes Challonge participant mapping for a user in a tournament
   */
  def removeChallongeParticipantMapping(tournamentId: Long, userId: Long): Future[Boolean]

  /**
   * Removes all Challonge participant mappings for a tournament
   */
  def removeAllChallongeParticipantMappings(tournamentId: Long): Future[Int]
}

@Singleton
class TournamentChallongeDAOImpl @Inject()(
                                            tournamentChallongeParticipantRepository: TournamentChallongeParticipantRepository,
                                            tournamentRegistrationRepository: TournamentRegistrationRepository,
                                            tournamentRepository: TournamentRepository,
                                            userRepository: UserRepository
                                          )(implicit ec: ExecutionContext) extends TournamentChallongeDAO {

  override def createChallongeParticipantMapping(
                                                  tournamentId: Long,
                                                  userId: Long,
                                                  challongeParticipantId: Long,
                                                  challongeTournamentId: Long
                                                ): Future[TournamentChallongeParticipant] = {
    val mapping = TournamentChallongeParticipant(
      tournamentId = tournamentId,
      userId = userId,
      challongeParticipantId = challongeParticipantId,
      challongeTournamentId = challongeTournamentId
    )
    tournamentChallongeParticipantRepository.create(mapping)
  }

  override def getChallongeParticipantId(tournamentId: Long, userId: Long): Future[Option[Long]] = {
    tournamentChallongeParticipantRepository
      .findByTournamentAndUser(tournamentId, userId)
      .map(_.map(_.challongeParticipantId))
  }

  override def getTournamentChallongeParticipants(tournamentId: Long): Future[List[TournamentChallongeParticipant]] = {
    tournamentChallongeParticipantRepository.findByTournamentId(tournamentId)
  }

  override def getTournamentUserByChallongeParticipantId(challongeParticipantId: Long): Future[Option[(Tournament, User)]] = {
    for {
      mappingOpt <- tournamentChallongeParticipantRepository.findByChallongeParticipantId(challongeParticipantId)
      result <- mappingOpt match {
        case Some(mapping) =>
          for {
            tournamentOpt <- tournamentRepository.findById(mapping.tournamentId)
            userOpt <- userRepository.getById(mapping.userId)
          } yield {
            for {
              tournament <- tournamentOpt
              user <- userOpt
            } yield (tournament, user)
          }
        case None => Future.successful(None)
      }
    } yield result
  }

  override def getTournamentParticipantsWithChallongeIds(tournamentId: Long): Future[List[(User, Option[Long])]] = {
    for {
      registrations <- tournamentRegistrationRepository.findByTournamentId(tournamentId)
      users <- Future.sequence(registrations.map(reg => userRepository.getById(reg.userId)))
      validUsers = users.flatten.toList
      challongeParticipants <- tournamentChallongeParticipantRepository.findByTournamentId(tournamentId)
      challongeMap = challongeParticipants.map(p => p.userId -> p.challongeParticipantId).toMap
    } yield {
      validUsers.map { user =>
        (user, challongeMap.get(user.userId))
      }
    }
  }

  override def hasChallongeParticipantId(tournamentId: Long, userId: Long): Future[Boolean] = {
    getChallongeParticipantId(tournamentId, userId).map(_.isDefined)
  }

  override def updateChallongeParticipantId(
                                             tournamentId: Long,
                                             userId: Long,
                                             newChallongeParticipantId: Long
                                           ): Future[Option[TournamentChallongeParticipant]] = {
    tournamentChallongeParticipantRepository
      .findByTournamentAndUser(tournamentId, userId)
      .flatMap {
        case Some(existing) =>
          val updated = existing.copy(
            challongeParticipantId = newChallongeParticipantId,
            updatedAt = Instant.now()
          )
          tournamentChallongeParticipantRepository.update(updated).map(Some(_))
        case None =>
          Future.successful(None)
      }
  }

  override def removeChallongeParticipantMapping(tournamentId: Long, userId: Long): Future[Boolean] = {
    tournamentChallongeParticipantRepository
      .findByTournamentAndUser(tournamentId, userId)
      .flatMap {
        case Some(mapping) =>
          tournamentChallongeParticipantRepository.delete(mapping.id)
        case None =>
          Future.successful(false)
      }
  }

  override def removeAllChallongeParticipantMappings(tournamentId: Long): Future[Int] = {
    tournamentChallongeParticipantRepository.deleteByTournamentId(tournamentId)
  }
}
