package models.repository

import models.{PotentialMatch, PotentialMatchStatus}
import models.component.PotentialMatchComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PotentialMatchRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) 
  extends PotentialMatchComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  // Expose the TableQuery for foreign key references
  def getTableQuery = potentialMatchesTable

  /**
   * Creates a new potential match.
   */
  def create(potentialMatch: PotentialMatch): Future[PotentialMatch] = db.run {
    createAction(potentialMatch)
  }

  /**
   * Creates a new potential match (DBIO action).
   */
  def createAction(potentialMatch: PotentialMatch): DBIO[PotentialMatch] = {
    (potentialMatchesTable += potentialMatch).map(_ => potentialMatch)
  }

  /**
   * Finds a potential match by its ID.
   */
  def findById(id: Long): Future[Option[PotentialMatch]] = db.run {
    findByIdAction(id)
  }

  /**
   * Finds a potential match by its ID (DBIO action).
   */
  def findByIdAction(id: Long): DBIO[Option[PotentialMatch]] = {
    potentialMatchesTable.filter(_.id === id).result.headOption
  }

  /**
   * Finds all potential matches for a specific user (either as first or second user).
   */
  def findByUserId(userId: Long): Future[List[PotentialMatch]] = db.run {
    findByUserIdAction(userId)
  }

  /**
   * Finds all potential matches for a specific user (DBIO action).
   */
  def findByUserIdAction(userId: Long): DBIO[List[PotentialMatch]] = {
    potentialMatchesTable
      .filter(pm => pm.firstUserId === userId || pm.secondUserId === userId)
      .sortBy(_.matchStartTime.asc)
      .result
      .map(_.toList)
  }

  /**
   * Finds potential matches with a specific status.
   */
  def findByStatus(status: PotentialMatchStatus): Future[List[PotentialMatch]] = db.run {
    findByStatusAction(status)
  }

  /**
   * Finds potential matches with a specific status (DBIO action).
   */
  def findByStatusAction(status: PotentialMatchStatus): DBIO[List[PotentialMatch]] = {
    potentialMatchesTable
      .filter(_.status === status)
      .sortBy(_.matchStartTime.asc)
      .result
      .map(_.toList)
  }

  /**
   * Updates the status of a potential match.
   */
  def updateStatus(id: Long, newStatus: PotentialMatchStatus): Future[Boolean] = db.run {
    updateStatusAction(id, newStatus)
  }

  /**
   * Updates the status of a potential match (DBIO action).
   */
  def updateStatusAction(id: Long, newStatus: PotentialMatchStatus): DBIO[Boolean] = {
    val query = potentialMatchesTable.filter(_.id === id)
    val update = query.map(_.status).update(newStatus)
    update.map(_ > 0)
  }

  /**
   * Finds potential matches between two specific users.
   */
  def findByUsers(firstUserId: Long, secondUserId: Long): Future[Option[PotentialMatch]] = db.run {
    findByUsersAction(firstUserId, secondUserId)
  }

  /**
   * Finds potential matches between two specific users (DBIO action).
   */
  def findByUsersAction(firstUserId: Long, secondUserId: Long): DBIO[Option[PotentialMatch]] = {
    potentialMatchesTable
      .filter(pm => (pm.firstUserId === firstUserId && pm.secondUserId === secondUserId) ||
                    (pm.firstUserId === secondUserId && pm.secondUserId === firstUserId))
      .result.headOption
  }

  /**
   * Finds all potential matches that are pending.
   */
  def findPendingMatches(): Future[List[PotentialMatch]] = db.run {
    findPendingMatchesAction()
  }

  /**
   * Finds all potential matches that are pending (DBIO action).
   */
  def findPendingMatchesAction(): DBIO[List[PotentialMatch]] = {
    findByStatusAction(PotentialMatchStatus.Potential)
  }

  /**
   * Updates the availability IDs for a potential match.
   */
  def updateAvailabilityIds(id: Long, firstAvailabilityId: Long, secondAvailabilityId: Long): Future[Boolean] = db.run {
    updateAvailabilityIdsAction(id, firstAvailabilityId, secondAvailabilityId)
  }

/**
   * Updates the availability IDs for a potential match.
   */
  def updateAvailabilityIdsAction(id: Long, firstAvailabilityId: Long, secondAvailabilityId: Long): DBIO[Boolean] = {
    val query = potentialMatchesTable.filter(_.id === id)
    val update1 = query.map(_.firstUserAvailabilityId).update(firstAvailabilityId)
    val update2 = query.map(_.secondUserAvailabilityId).update(secondAvailabilityId)
    DBIO.sequence(Seq(update1, update2)).map(results => results.exists(_ > 0))
  }

  /**
   * Deletes a potential match.
   */
  def delete(id: Long): Future[Boolean] = db.run {
    deleteAction(id)
  }

  /**
   * Deletes a potential match (DBIO action).
   */
  def deleteAction(id: Long): DBIO[Boolean] = {
    val query = potentialMatchesTable.filter(_.id === id)
    val delete = query.delete
    delete.map(_ > 0)
  }
}