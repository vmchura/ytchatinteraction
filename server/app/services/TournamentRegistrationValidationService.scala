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
    * Requires 2 replays per matchup (6 total: 2 vs each race including mirror).
    *
    * @param userId
    *   The user ID
    * @param race
    *   The StarCraft race (Protoss, Zerg, or Terran)
    * @param minReplaysPerMatchup
    *   Minimum number of replays required per matchup (default: 2)
    * @return
    *   Future[Boolean] True if user has enough replays for all matchups
    */
  def hasEnoughReplays(userId: Long, race: SCRace, minReplaysPerMatchup: Int = 2): Future[Boolean]

  /** Gets the replay counts per matchup for a user's race.
    *
    * @param userId
    *   The user ID
    * @param race
    *   The StarCraft race
    * @return
    *   Future[Map[SCRace, Int]] Map of rival race to replay count
    */
  def getReplayCountsPerMatchup(userId: Long, race: SCRace): Future[Map[SCRace, Int]]

  /** Checks if user has added any availability times.
   *
   * @param userId
   *   The user ID
   * @return
   *   Future[Boolean] True if user has availability times
   */
  def hasAvailabilityTimes(userId: Long): Future[Boolean]

  /** Checks if user has selected a timezone.
   *
   * @param userId
   *   The user ID
   * @return
   *   Future[Boolean] True if user has selected a timezone
   */
  def hasTimezone(userId: Long): Future[Boolean]

  /** Validates all registration requirements for a user.
   *
   * @param userId
   *   The user ID
   * @param racePicked
   *   The race selected by the user (Protoss, Zerg, or Terran)
   * @return
   *   Future[Boolean] True if user meets all requirements
   */
  def isUserAbleToRegister(userId: Long, racePicked: SCRace): Future[Boolean]
}

@Singleton
class TournamentRegistrationValidationServiceImpl @Inject() (
    analyticalFileRepository: AnalyticalFileRepository,
    userAvailabilityRepository: UserAvailabilityRepository
)(implicit ec: ExecutionContext)
    extends TournamentRegistrationValidationService {

  override def hasTimezone(userId: Long): Future[Boolean] = {
    userAvailabilityRepository
      .getTimezone(userId)
      .map(_.isDefined)
  }

  override def hasEnoughReplays(userId: Long, race: SCRace, minReplaysPerMatchup: Int = 2): Future[Boolean] = {
    for {
      replayCounts <- getReplayCountsPerMatchup(userId, race)
    } yield {
      // Check if user has at least minReplaysPerMatchup for each of the 3 matchups
      val allRaces = Seq(Protoss, Zerg, Terran)
      allRaces.forall(rivalRace => replayCounts.getOrElse(rivalRace, 0) >= minReplaysPerMatchup)
    }
  }

  override def getReplayCountsPerMatchup(userId: Long, race: SCRace): Future[Map[SCRace, Int]] = {
    analyticalFileRepository
      .findByUserRace(userId = userId, race = race)
      .map { files =>
        // Group by rival race and count
        files.groupBy(_.rivalRace).view.mapValues(_.size).toMap
      }
  }

  override def hasAvailabilityTimes(userId: Long): Future[Boolean] = {
    userAvailabilityRepository
      .getAllAvailabilitiesByUserId(userId)
      .map(_.nonEmpty)
  }

  override def isUserAbleToRegister(userId: Long, racePicked: SCRace): Future[Boolean] = {
    for {
      hasReplays     <- hasEnoughReplays(userId, racePicked, minReplaysPerMatchup = 2)
      hasAvailabilities <- hasAvailabilityTimes(userId)
      hasTimezoneSet <- hasTimezone(userId)
    } yield {
      hasReplays && hasAvailabilities && hasTimezoneSet
    }
  }
}
