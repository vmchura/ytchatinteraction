package services

import models.StarCraftModels.*
import models.repository.{AnalyticalFileRepository, UserAvailabilityRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/** Service for validating tournament registration requirements.
  * This service is separate from TournamentService to avoid circular dependencies.
  */
trait TournamentRegistrationValidationService {

  /** Checks if user has enough base replays for a given race.
    *
    * @param userId
    *   The user ID
    * @param race
    *   The StarCraft race (Protoss, Zerg, or Terran)
    * @param minReplays
    *   Minimum number of replays required (default: 2)
    * @return
    *   Future[Boolean] True if user has enough replays
    */
  def hasEnoughReplays(userId: Long, race: SCRace, minReplays: Int = 2): Future[Boolean]

  /** Checks if user has added any availability times.
    *
    * @param userId
    *   The user ID
    * @return
    *   Future[Boolean] True if user has availability times
    */
  def hasAvailabilityTimes(userId: Long): Future[Boolean]

  /** Validates all registration requirements for a user.
    *
    * @param userId
    *   The user ID
    * @param racePicked
    *   The race selected by the user (Protoss, Zerg, or Terran)
    * @return
    *   Future[Boolean] True if user meets all requirements
    */
  def isUserAbleToRegister(userId: Long, racePicked: String): Future[Boolean]
}

@Singleton
class TournamentRegistrationValidationServiceImpl @Inject() (
    analyticalFileRepository: AnalyticalFileRepository,
    userAvailabilityRepository: UserAvailabilityRepository
)(implicit ec: ExecutionContext)
    extends TournamentRegistrationValidationService {

  override def hasEnoughReplays(userId: Long, race: SCRace, minReplays: Int = 2): Future[Boolean] = {
    analyticalFileRepository
      .findByUserRace(userId = userId, race = race)
      .map(_.length >= minReplays)
  }

  override def hasAvailabilityTimes(userId: Long): Future[Boolean] = {
    userAvailabilityRepository
      .getAllAvailabilitiesByUserId(userId)
      .map(_.nonEmpty)
  }

  override def isUserAbleToRegister(userId: Long, racePicked: String): Future[Boolean] = {
    val raceOpt = racePicked match {
      case "Protoss" => Some(Protoss)
      case "Zerg"    => Some(Zerg)
      case "Terran"  => Some(Terran)
      case _         => None
    }

    raceOpt match {
      case None => Future.successful(false)
      case Some(race) =>
        for {
          hasReplays     <- hasEnoughReplays(userId, race, minReplays = 2)
          hasAvailabilities <- hasAvailabilityTimes(userId)
        } yield {
          hasReplays && hasAvailabilities
        }
    }
  }
}
