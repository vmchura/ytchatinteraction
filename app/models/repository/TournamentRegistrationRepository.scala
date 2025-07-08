package models.repository

import models.{TournamentRegistration, RegistrationStatus, Tournament, User}
import models.component.TournamentRegistrationComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class TournamentRegistrationRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) 
  extends TournamentRegistrationComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  // Expose the TableQuery for foreign key references
  def getTableQuery = tournamentRegistrationsTable

  /**
   * Creates a new tournament registration.
   */
  def create(registration: TournamentRegistration): Future[TournamentRegistration] = db.run {
    createAction(registration)
  }

  /**
   * Creates a new tournament registration (DBIO action).
   */
  def createAction(registration: TournamentRegistration): DBIO[TournamentRegistration] = {
    (tournamentRegistrationsTable returning tournamentRegistrationsTable.map(_.id) into ((registration, id) => registration.copy(id = id))) += registration
  }

  /**
   * Finds a tournament registration by its ID.
   */
  def findById(id: Long): Future[Option[TournamentRegistration]] = db.run {
    findByIdAction(id)
  }

  /**
   * Finds a tournament registration by its ID (DBIO action).
   */
  def findByIdAction(id: Long): DBIO[Option[TournamentRegistration]] = {
    tournamentRegistrationsTable.filter(_.id === id).result.headOption
  }

  /**
   * Finds a registration by tournament ID and user ID.
   */
  def findByTournamentAndUser(tournamentId: Long, userId: Long): Future[Option[TournamentRegistration]] = db.run {
    findByTournamentAndUserAction(tournamentId, userId)
  }

  /**
   * Finds a registration by tournament ID and user ID (DBIO action).
   */
  def findByTournamentAndUserAction(tournamentId: Long, userId: Long): DBIO[Option[TournamentRegistration]] = {
    tournamentRegistrationsTable
      .filter(r => r.tournamentId === tournamentId && r.userId === userId)
      .result
      .headOption
  }

  /**
   * Finds all registrations for a specific tournament.
   */
  def findByTournamentId(tournamentId: Long): Future[List[TournamentRegistration]] = db.run {
    findByTournamentIdAction(tournamentId)
  }

  /**
   * Finds all registrations for a specific tournament (DBIO action).
   */
  def findByTournamentIdAction(tournamentId: Long): DBIO[List[TournamentRegistration]] = {
    tournamentRegistrationsTable
      .filter(_.tournamentId === tournamentId)
      .sortBy(_.registeredAt.asc)
      .result
      .map(_.toList)
  }

  /**
   * Finds registrations with user details for a specific tournament.
   */
  def findWithUsersByTournamentId(tournamentId: Long): Future[List[(TournamentRegistration, User)]] = db.run {
    findWithUsersByTournamentIdAction(tournamentId)
  }

  /**
   * Finds registrations with user details for a specific tournament (DBIO action).
   */
  def findWithUsersByTournamentIdAction(tournamentId: Long): DBIO[List[(TournamentRegistration, User)]] = {
    val query = for {
      registration <- tournamentRegistrationsTable.filter(_.tournamentId === tournamentId)
      user <- usersTable.filter(_.userId === registration.userId)
    } yield (registration, user)
    
    query.sortBy(_._1.registeredAt.asc).result.map(_.toList)
  }

  /**
   * Finds active registrations (Registered or Confirmed) for a tournament.
   */
  def findActiveRegistrations(tournamentId: Long): Future[List[TournamentRegistration]] = db.run {
    findActiveRegistrationsAction(tournamentId)
  }

  /**
   * Finds active registrations (Registered or Confirmed) for a tournament (DBIO action).
   */
  def findActiveRegistrationsAction(tournamentId: Long): DBIO[List[TournamentRegistration]] = {
    tournamentRegistrationsTable
      .filter(r => r.tournamentId === tournamentId && 
                  (r.status === RegistrationStatus.Registered || r.status === RegistrationStatus.Confirmed))
      .sortBy(_.registeredAt.asc)
      .result
      .map(_.toList)
  }

  /**
   * Updates registration status.
   */
  def updateStatus(id: Long, newStatus: RegistrationStatus): Future[Option[TournamentRegistration]] = db.run {
    updateStatusAction(id, newStatus)
  }

  /**
   * Updates registration status (DBIO action).
   */
  def updateStatusAction(id: Long, newStatus: RegistrationStatus): DBIO[Option[TournamentRegistration]] = {
    val updateQuery = tournamentRegistrationsTable.filter(_.id === id).map(_.status)
    for {
      rowsUpdated <- updateQuery.update(newStatus)
      updatedRegistration <- if (rowsUpdated > 0) findByIdAction(id) else DBIO.successful(None)
    } yield updatedRegistration
  }

  /**
   * Deletes a registration by tournament and user ID.
   */
  def deleteByTournamentAndUser(tournamentId: Long, userId: Long): Future[Boolean] = db.run {
    deleteByTournamentAndUserAction(tournamentId, userId)
  }

  /**
   * Deletes a registration by tournament and user ID (DBIO action).
   */
  def deleteByTournamentAndUserAction(tournamentId: Long, userId: Long): DBIO[Boolean] = {
    tournamentRegistrationsTable
      .filter(r => r.tournamentId === tournamentId && r.userId === userId)
      .delete
      .map(_ > 0)
  }

  /**
   * Checks if a user is registered for a tournament.
   */
  def isUserRegistered(tournamentId: Long, userId: Long): Future[Boolean] = db.run {
    isUserRegisteredAction(tournamentId, userId)
  }

  /**
   * Checks if a user is registered for a tournament (DBIO action).
   */
  def isUserRegisteredAction(tournamentId: Long, userId: Long): DBIO[Boolean] = {
    tournamentRegistrationsTable
      .filter(r => r.tournamentId === tournamentId && r.userId === userId && 
                  (r.status === RegistrationStatus.Registered || r.status === RegistrationStatus.Confirmed))
      .exists
      .result
  }

  /**
   * Counts active registrations for a specific tournament.
   */
  def countActiveRegistrations(tournamentId: Long): Future[Int] = db.run {
    countActiveRegistrationsAction(tournamentId)
  }

  /**
   * Counts active registrations for a specific tournament (DBIO action).
   */
  def countActiveRegistrationsAction(tournamentId: Long): DBIO[Int] = {
    tournamentRegistrationsTable
      .filter(r => r.tournamentId === tournamentId && 
                  (r.status === RegistrationStatus.Registered || r.status === RegistrationStatus.Confirmed))
      .length
      .result
  }
}
