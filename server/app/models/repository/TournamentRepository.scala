package models.repository

import models.{Tournament, TournamentStatus}
import models.component.TournamentComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class TournamentRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) 
  extends TournamentComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  // Expose the TableQuery for foreign key references
  def getTableQuery = tournamentsTable

  /**
   * Creates a new tournament.
   */
  def create(tournament: Tournament): Future[Tournament] = db.run {
    createAction(tournament)
  }

  /**
   * Creates a new tournament (DBIO action).
   */
  def createAction(tournament: Tournament): DBIO[Tournament] = {
    (tournamentsTable returning tournamentsTable.map(_.id) into ((tournament, id) => tournament.copy(id = id))) += tournament
  }

  /**
   * Finds a tournament by its ID.
   */
  def findById(id: Long): Future[Option[Tournament]] = db.run {
    findByIdAction(id)
  }

  /**
   * Finds a tournament by its ID (DBIO action).
   */
  def findByIdAction(id: Long): DBIO[Option[Tournament]] = {
    tournamentsTable.filter(_.id === id).result.headOption
  }

  /**
   * Finds a tournament by its Challonge tournament ID.
   */
  def findByChallongeId(challongeTournamentId: Long): Future[Option[Tournament]] = db.run {
    findByChallongeIdAction(challongeTournamentId)
  }

  /**
   * Finds a tournament by its Challonge tournament ID (DBIO action).
   */
  def findByChallongeIdAction(challongeTournamentId: Long): DBIO[Option[Tournament]] = {
    tournamentsTable.filter(_.challongeTournamentId === challongeTournamentId).result.headOption
  }

  /**
   * Finds tournaments by status.
   */
  def findByStatus(status: TournamentStatus): Future[List[Tournament]] = db.run {
    findByStatusAction(status)
  }

  /**
   * Finds tournaments by status (DBIO action).
   */
  def findByStatusAction(status: TournamentStatus): DBIO[List[Tournament]] = {
    tournamentsTable
      .filter(_.status === status)
      .sortBy(_.createdAt.desc)
      .result
      .map(_.toList)
  }

  /**
   * Finds tournaments that are open for registration.
   */
  def findOpenForRegistration(): Future[List[Tournament]] = db.run {
    findOpenForRegistrationAction()
  }

  /**
   * Finds tournaments that are open for registration (DBIO action).
   */
  def findOpenForRegistrationAction(): DBIO[List[Tournament]] = {
    val now = Instant.now()
    tournamentsTable
      .filter(tournament => 
        tournament.status === TournamentStatus.RegistrationOpen &&
        tournament.registrationStartAt <= now &&
        tournament.registrationEndAt > now
      )
      .sortBy(_.registrationStartAt.asc)
      .result
      .map(_.toList)
  }

  /**
   * Finds tournaments whose registration period has ended.
   */
  def findRegistrationEnded(): Future[List[Tournament]] = db.run {
    findRegistrationEndedAction()
  }

  /**
   * Finds tournaments whose registration period has ended (DBIO action).
   */
  def findRegistrationEndedAction(): DBIO[List[Tournament]] = {
    val now = Instant.now()
    tournamentsTable
      .filter(tournament => 
        tournament.status === TournamentStatus.RegistrationOpen &&
        tournament.registrationEndAt <= now
      )
      .sortBy(_.registrationEndAt.asc)
      .result
      .map(_.toList)
  }

  /**
   * Updates a tournament.
   */
  def update(tournament: Tournament): Future[Option[Tournament]] = db.run {
    updateAction(tournament)
  }

  /**
   * Updates a tournament (DBIO action).
   */
  def updateAction(tournament: Tournament): DBIO[Option[Tournament]] = {
    val updatedTournament = tournament.copy(updatedAt = Instant.now())
    val updateQuery = tournamentsTable.filter(_.id === tournament.id)
    for {
      rowsUpdated <- updateQuery.update(updatedTournament)
      result <- if (rowsUpdated > 0) DBIO.successful(Some(updatedTournament)) else DBIO.successful(None)
    } yield result
  }

  /**
   * Updates tournament status.
   */
  def updateStatus(id: Long, newStatus: TournamentStatus): Future[Option[Tournament]] = db.run {
    updateStatusAction(id, newStatus)
  }

  /**
   * Updates tournament status (DBIO action).
   */
  def updateStatusAction(id: Long, newStatus: TournamentStatus): DBIO[Option[Tournament]] = {
    val now = Instant.now()
    val updateQuery = tournamentsTable.filter(_.id === id).map(t => (t.status, t.updatedAt))
    for {
      rowsUpdated <- updateQuery.update((newStatus, now))
      updatedTournament <- if (rowsUpdated > 0) findByIdAction(id) else DBIO.successful(None)
    } yield updatedTournament
  }

  /**
   * Updates the Challonge tournament ID.
   */
  def updateChallongeId(id: Long, challongeTournamentId: Long): Future[Option[Tournament]] = db.run {
    updateChallongeIdAction(id, challongeTournamentId)
  }

  /**
   * Updates the Challonge tournament ID (DBIO action).
   */
  def updateChallongeIdAction(id: Long, challongeTournamentId: Long): DBIO[Option[Tournament]] = {
    val now = Instant.now()
    val updateQuery = tournamentsTable.filter(_.id === id).map(t => (t.challongeTournamentId, t.updatedAt))
    for {
      rowsUpdated <- updateQuery.update((Some(challongeTournamentId), now))
      updatedTournament <- if (rowsUpdated > 0) findByIdAction(id) else DBIO.successful(None)
    } yield updatedTournament
  }

  /**
   * Deletes a tournament by its ID.
   */
  def delete(id: Long): Future[Boolean] = db.run {
    deleteAction(id)
  }

  /**
   * Deletes a tournament by its ID (DBIO action).
   */
  def deleteAction(id: Long): DBIO[Boolean] = {
    tournamentsTable.filter(_.id === id).delete.map(_ > 0)
  }

  /**
   * Lists all tournaments.
   */
  def list(): Future[Seq[Tournament]] = db.run {
    listAction()
  }

  /**
   * Lists all tournaments (DBIO action).
   */
  def listAction(): DBIO[Seq[Tournament]] = {
    tournamentsTable.sortBy(_.createdAt.desc).result
  }

  /**
   * Lists tournaments with pagination.
   */
  def listPaginated(offset: Int, limit: Int): Future[Seq[Tournament]] = db.run {
    listPaginatedAction(offset, limit)
  }

  /**
   * Lists tournaments with pagination (DBIO action).
   */
  def listPaginatedAction(offset: Int, limit: Int): DBIO[Seq[Tournament]] = {
    tournamentsTable
      .sortBy(_.createdAt.desc)
      .drop(offset)
      .take(limit)
      .result
  }

  /**
   * Checks if a tournament exists by its ID.
   */
  def exists(id: Long): Future[Boolean] = db.run {
    existsAction(id)
  }

  /**
   * Checks if a tournament exists by its ID (DBIO action).
   */
  def existsAction(id: Long): DBIO[Boolean] = {
    tournamentsTable.filter(_.id === id).exists.result
  }

  /**
   * Counts all tournaments.
   */
  def count(): Future[Int] = db.run {
    countAction()
  }

  /**
   * Counts all tournaments (DBIO action).
   */
  def countAction(): DBIO[Int] = {
    tournamentsTable.length.result
  }

  /**
   * Counts tournaments by status.
   */
  def countByStatus(status: TournamentStatus): Future[Int] = db.run {
    countByStatusAction(status)
  }

  /**
   * Counts tournaments by status (DBIO action).
   */
  def countByStatusAction(status: TournamentStatus): DBIO[Int] = {
    tournamentsTable.filter(_.status === status).length.result
  }
}
