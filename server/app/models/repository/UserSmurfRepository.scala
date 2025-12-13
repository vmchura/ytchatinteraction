package models.repository

import models.UserSmurf
import models.component.UserSmurfComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserSmurfRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) 
  extends UserSmurfComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  // Expose the TableQuery for foreign key references
  def getTableQuery = userSmurfsTable

  /**
   * Creates a new user smurf record.
   */
  def create(userSmurf: UserSmurf): Future[UserSmurf] = db.run {
    createAction(userSmurf)
  }

  /**
   * Creates a new user smurf record (DBIO action).
   */
  def createAction(userSmurf: UserSmurf): DBIO[UserSmurf] = {
    (userSmurfsTable.map(us => (us.matchId, us.tournamentId, us.casualMatchId, us.userId, us.smurf, us.createdAt))
      returning userSmurfsTable.map(_.id)
      into ((fields, id) => UserSmurf(id, fields._1, fields._2, fields._3, fields._4, fields._5, fields._6))
    ) += (userSmurf.matchId, userSmurf.tournamentId, userSmurf.casualMatchId, userSmurf.userId, userSmurf.smurf, userSmurf.createdAt)
  }

  /**
   * Creates multiple user smurf records in a batch.
   */
  def createBatch(userSmurfs: Seq[UserSmurf]): Future[Seq[UserSmurf]] = db.run {
    createBatchAction(userSmurfs)
  }

  /**
   * Creates multiple user smurf records in a batch (DBIO action).
   */
  def createBatchAction(userSmurfs: Seq[UserSmurf]): DBIO[Seq[UserSmurf]] = {
    val insertData = userSmurfs.map(us => (us.matchId, us.tournamentId, us.casualMatchId, us.userId, us.smurf, us.createdAt))
    
    (userSmurfsTable.map(us => (us.matchId, us.tournamentId, us.casualMatchId, us.userId, us.smurf, us.createdAt))
      returning userSmurfsTable.map(_.id)
    ).++=(insertData).map { ids =>
      userSmurfs.zip(ids).map { case (original, id) =>
        original.copy(id = id)
      }
    }
  }

  /**
   * Finds a user smurf record by its ID.
   */
  def findById(id: Long): Future[Option[UserSmurf]] = db.run {
    findByIdAction(id)
  }

  /**
   * Finds a user smurf record by its ID (DBIO action).
   */
  def findByIdAction(id: Long): DBIO[Option[UserSmurf]] = {
    userSmurfsTable.filter(_.id === id).result.headOption
  }

  /**
   * Finds all user smurf records for a specific match.
   */
  def findByMatchId(matchId: Long): Future[List[UserSmurf]] = db.run {
    findByMatchIdAction(matchId)
  }

  def findByCasualMatchId(casualMatchId: Long): Future[List[UserSmurf]] = db.run {
    userSmurfsTable
      .filter(_.casualMatchId === casualMatchId)
      .sortBy(_.createdAt.desc)
      .result
      .map(_.toList)
  }

  /**
   * Finds all user smurf records for a specific match (DBIO action).
   */
  def findByMatchIdAction(matchId: Long): DBIO[List[UserSmurf]] = {
    userSmurfsTable
      .filter(_.matchId === matchId)
      .sortBy(_.createdAt.desc)
      .result
      .map(_.toList)
  }

  /**
   * Finds all user smurf records for a specific tournament.
   */
  def findByTournamentId(tournamentId: Long): Future[List[UserSmurf]] = db.run {
    findByTournamentIdAction(tournamentId)
  }

  /**
   * Finds all user smurf records for a specific tournament (DBIO action).
   */
  def findByTournamentIdAction(tournamentId: Long): DBIO[List[UserSmurf]] = {
    userSmurfsTable
      .filter(_.tournamentId === tournamentId)
      .sortBy(_.createdAt.desc)
      .result
      .map(_.toList)
  }

  /**
   * Finds all user smurf records for a specific user.
   */
  def findByUserId(userId: Long): Future[List[UserSmurf]] = db.run {
    findByUserIdAction(userId)
  }

  /**
   * Finds all user smurf records for a specific user (DBIO action).
   */
  def findByUserIdAction(userId: Long): DBIO[List[UserSmurf]] = {
    userSmurfsTable
      .filter(_.userId === userId)
      .sortBy(_.createdAt.desc)
      .result
      .map(_.toList)
  }

  /**
   * Finds user smurf records by match ID and user ID.
   */
  def findByMatchIdAndUserId(matchId: Long, userId: Long): Future[Option[UserSmurf]] = db.run {
    findByMatchIdAndUserIdAction(matchId, userId)
  }

  /**
   * Finds user smurf records by match ID and user ID (DBIO action).
   */
  def findByMatchIdAndUserIdAction(matchId: Long, userId: Long): DBIO[Option[UserSmurf]] = {
    userSmurfsTable
      .filter(us => us.matchId === matchId && us.userId === userId)
      .result
      .headOption
  }

  /**
   * Finds all user smurf records by tournament ID and user ID.
   */
  def findByTournamentIdAndUserId(tournamentId: Long, userId: Long): Future[List[UserSmurf]] = db.run {
    findByTournamentIdAndUserIdAction(tournamentId, userId)
  }

  /**
   * Finds all user smurf records by tournament ID and user ID (DBIO action).
   */
  def findByTournamentIdAndUserIdAction(tournamentId: Long, userId: Long): DBIO[List[UserSmurf]] = {
    userSmurfsTable
      .filter(us => us.tournamentId === tournamentId && us.userId === userId)
      .sortBy(_.createdAt.desc)
      .result
      .map(_.toList)
  }

  /**
   * Gets all unique smurfs used by a user in a tournament.
   */
  def getUniqueSmurfsByTournamentAndUser(tournamentId: Long, userId: Long): Future[List[String]] = db.run {
    getUniqueSmurfsByTournamentAndUserAction(tournamentId, userId)
  }

  /**
   * Gets all unique smurfs used by a user in a tournament (DBIO action).
   */
  def getUniqueSmurfsByTournamentAndUserAction(tournamentId: Long, userId: Long): DBIO[List[String]] = {
    userSmurfsTable
      .filter(us => us.tournamentId === tournamentId && us.userId === userId)
      .map(_.smurf)
      .distinct
      .result
      .map(_.toList)
  }

  /**
   * Gets all unique smurfs used in a tournament.
   */
  def getUniqueSmurfsByTournament(tournamentId: Long): Future[List[String]] = db.run {
    getUniqueSmurfsByTournamentAction(tournamentId)
  }

  /**
   * Gets all unique smurfs used in a tournament (DBIO action).
   */
  def getUniqueSmurfsByTournamentAction(tournamentId: Long): DBIO[List[String]] = {
    userSmurfsTable
      .filter(_.tournamentId === tournamentId)
      .map(_.smurf)
      .distinct
      .result
      .map(_.toList)
  }

  /**
   * Deletes a user smurf record by its ID.
   */
  def delete(id: Long): Future[Boolean] = db.run {
    deleteAction(id)
  }

  /**
   * Deletes a user smurf record by its ID (DBIO action).
   */
  def deleteAction(id: Long): DBIO[Boolean] = {
    userSmurfsTable.filter(_.id === id).delete.map(_ > 0)
  }

  /**
   * Deletes all user smurf records for a specific match.
   */
  def deleteByMatchId(matchId: Long): Future[Int] = db.run {
    deleteByMatchIdAction(matchId)
  }

  /**
   * Deletes all user smurf records for a specific match (DBIO action).
   */
  def deleteByMatchIdAction(matchId: Long): DBIO[Int] = {
    userSmurfsTable.filter(_.matchId === matchId).delete
  }

  /**
   * Lists all user smurf records.
   */
  def list(): Future[Seq[UserSmurf]] = db.run {
    listAction()
  }

  /**
   * Lists all user smurf records (DBIO action).
   */
  def listAction(): DBIO[Seq[UserSmurf]] = {
    userSmurfsTable.sortBy(_.createdAt.desc).result
  }

  /**
   * Checks if a user smurf record exists by its ID.
   */
  def exists(id: Long): Future[Boolean] = db.run {
    existsAction(id)
  }

  /**
   * Checks if a user smurf record exists by its ID (DBIO action).
   */
  def existsAction(id: Long): DBIO[Boolean] = {
    userSmurfsTable.filter(_.id === id).exists.result
  }

  /**
   * Counts user smurf records for a specific match.
   */
  def countByMatchId(matchId: Long): Future[Int] = db.run {
    countByMatchIdAction(matchId)
  }

  /**
   * Counts user smurf records for a specific match (DBIO action).
   */
  def countByMatchIdAction(matchId: Long): DBIO[Int] = {
    userSmurfsTable.filter(_.matchId === matchId).length.result
  }

  /**
   * Counts user smurf records for a specific tournament.
   */
  def countByTournamentId(tournamentId: Long): Future[Int] = db.run {
    countByTournamentIdAction(tournamentId)
  }

  /**
   * Counts user smurf records for a specific tournament (DBIO action).
   */
  def countByTournamentIdAction(tournamentId: Long): DBIO[Int] = {
    userSmurfsTable.filter(_.tournamentId === tournamentId).length.result
  }
}
