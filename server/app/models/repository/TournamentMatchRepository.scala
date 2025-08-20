package models.repository

import evolutioncomplete.WinnerShared
import evolutioncomplete.WinnerShared.*
import models.{MatchStatus, TournamentMatch}
import models.component.TournamentMatchComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TournamentMatchRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) 
  extends TournamentMatchComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  // Expose the TableQuery for foreign key references
  def getTableQuery = tournamentMatchesTable

  /**
   * Creates a new tournament match.
   */
  def create(tournamentMatch: TournamentMatch): Future[TournamentMatch] = db.run {
    createAction(tournamentMatch)
  }

  /**
   * Creates a new tournament match (DBIO action).
   */
  def createAction(tournamentMatch: TournamentMatch): DBIO[TournamentMatch] = {
    (tournamentMatchesTable += tournamentMatch).map(_ => tournamentMatch)
  }

  /**
   * Finds a tournament match by its ID.
   */
  def findById(challongeMatchID: Long): Future[Option[TournamentMatch]] = db.run {
    findByIdAction(challongeMatchID)
  }

  /**
   * Finds a tournament match by its ID (DBIO action).
   */
  def findByIdAction(challongeMatchID: Long): DBIO[Option[TournamentMatch]] = {
    tournamentMatchesTable.filter(_.matchId === challongeMatchID).result.headOption
  }

  /**
   * Finds all tournament matches for a specific tournament.
   */
  def findByTournamentId(tournamentId: Long): Future[List[TournamentMatch]] = db.run {
    findByTournamentIdAction(tournamentId)
  }

  /**
   * Finds all tournament matches for a specific tournament (DBIO action).
   */
  def findByTournamentIdAction(tournamentId: Long): DBIO[List[TournamentMatch]] = {
    tournamentMatchesTable
      .filter(_.tournamentId === tournamentId)
      .sortBy(_.createdAt.desc)
      .result
      .map(_.toList)
  }

  /**
   * Finds all tournament matches where a user is participating.
   */
  def findByUserId(userId: Long): Future[List[TournamentMatch]] = db.run {
    findByUserIdAction(userId)
  }

  /**
   * Finds all tournament matches where a user is participating (DBIO action).
   */
  def findByUserIdAction(userId: Long): DBIO[List[TournamentMatch]] = {
    tournamentMatchesTable
      .filter(tournamentMatch => tournamentMatch.firstUserId === userId || tournamentMatch.secondUserId === userId)
      .sortBy(_.createdAt.desc)
      .result
      .map(_.toList)
  }

  /**
   * Finds all tournament matches with a specific status.
   */
  def findByStatus(status: MatchStatus): Future[List[TournamentMatch]] = db.run {
    findByStatusAction(status)
  }

  /**
   * Finds all tournament matches with a specific status (DBIO action).
   */
  def findByStatusAction(status: MatchStatus): DBIO[List[TournamentMatch]] = {
    tournamentMatchesTable
      .filter(_.status === status)
      .sortBy(_.createdAt.desc)
      .result
      .map(_.toList)
  }

  /**
   * Finds tournament matches by tournament ID and status.
   */
  def findByTournamentIdAndStatus(tournamentId: Long, status: MatchStatus): Future[List[TournamentMatch]] = db.run {
    findByTournamentIdAndStatusAction(tournamentId, status)
  }

  /**
   * Finds tournament matches by tournament ID and status (DBIO action).
   */
  def findByTournamentIdAndStatusAction(tournamentId: Long, status: MatchStatus): DBIO[List[TournamentMatch]] = {
    tournamentMatchesTable
      .filter(matchTournament => matchTournament.tournamentId === tournamentId && matchTournament.status === status)
      .sortBy(_.createdAt.desc)
      .result
      .map(_.toList)
  }

  /**
   * Updates the status of a tournament match.
   */
  def updateStatus(matchId: Long, newStatus: MatchStatus): Future[Option[TournamentMatch]] = db.run {
    updateStatusAction(matchId, newStatus)
  }

  /**
   * Updates the status of a tournament match (DBIO action).
   */
  def updateStatusAction(matchId: Long, newStatus: MatchStatus): DBIO[Option[TournamentMatch]] = {
    val updateQuery = tournamentMatchesTable.filter(_.matchId === matchId).map(_.status)
    for {
      rowsUpdated <- updateQuery.update(newStatus)
      updatedMatch <- if (rowsUpdated > 0) findByIdAction(matchId) else DBIO.successful(None)
    } yield updatedMatch
  }

  /**
   * Deletes a tournament match by its ID.
   */
  def delete(matchId: Long): Future[Boolean] = db.run {
    deleteAction(matchId)
  }

  /**
   * Deletes a tournament match by its ID (DBIO action).
   */
  def deleteAction(matchId: Long): DBIO[Boolean] = {
    tournamentMatchesTable.filter(_.matchId === matchId).delete.map(_ > 0)
  }

  /**
   * Lists all tournament matches.
   */
  def list(): Future[Seq[TournamentMatch]] = db.run {
    listAction()
  }

  /**
   * Lists all tournament matches (DBIO action).
   */
  def listAction(): DBIO[Seq[TournamentMatch]] = {
    tournamentMatchesTable.sortBy(_.createdAt.desc).result
  }

  /**
   * Checks if a tournament match exists by its ID.
   */
  def exists(matchId: Long): Future[Boolean] = db.run {
    existsAction(matchId)
  }

  /**
   * Checks if a tournament match exists by its ID (DBIO action).
   */
  def existsAction(matchId: Long): DBIO[Boolean] = {
    tournamentMatchesTable.filter(_.matchId === matchId).exists.result
  }

  /**
   * Updates a tournament match.
   */
  def update(tournamentMatch: TournamentMatch): Future[Option[TournamentMatch]] = db.run {
    updateAction(tournamentMatch)
  }

  /**
   * Updates a tournament match (DBIO action).
   */
  def updateAction(tournamentMatch: TournamentMatch): DBIO[Option[TournamentMatch]] = {
    val updateQuery = tournamentMatchesTable.filter(_.matchId === tournamentMatch.matchId)
    for {
      rowsUpdated <- updateQuery.update(tournamentMatch)
      updatedMatch <- if (rowsUpdated > 0) DBIO.successful(Some(tournamentMatch)) else DBIO.successful(None)
    } yield updatedMatch
  }

  /**
   * Counts tournament matches for a specific tournament.
   */
  def countByTournamentId(tournamentId: Long): Future[Int] = db.run {
    countByTournamentIdAction(tournamentId)
  }

  /**
   * Counts tournament matches for a specific tournament (DBIO action).
   */
  def countByTournamentIdAction(tournamentId: Long): DBIO[Int] = {
    tournamentMatchesTable.filter(_.tournamentId === tournamentId).length.result
  }

  /**
   * Counts tournament matches by status.
   */
  def countByStatus(status: MatchStatus): Future[Int] = db.run {
    countByStatusAction(status)
  }

  /**
   * Counts tournament matches by status (DBIO action).
   */
  def countByStatusAction(status: MatchStatus): DBIO[Int] = {
    tournamentMatchesTable.filter(_.status === status).length.result
  }

  /**
   * Updates the winner and status of a tournament match.
   */
  def updateWinnerAndStatus(tournamentMatch: TournamentMatch, winner: WinnerShared): Future[Boolean] = db.run {
    updateWinnerAndStatusAction(tournamentMatch, winner)
  }

  /**
   * Updates the winner and status of a tournament match (DBIO action).
   */
  def updateWinnerAndStatusAction(tournamentMatch: TournamentMatch, winner: WinnerShared): DBIO[Boolean] = {
    val updateQuery = tournamentMatchesTable.filter(_.matchId === tournamentMatch.matchId)
      .map(m => (m.winnerUserId, m.status, m.winner_description))
    for {
      _ <- if(winner == Undefined) DBIO.failed(new IllegalStateException("Undefined status to update as Match Finished")) else DBIO.successful("")
      rowsUpdated <- updateQuery.update{
        winner match {
          case FirstUser | FirstUserByOnlyPresented => (Some(tournamentMatch.firstUserId), MatchStatus.Completed, winner)
          case SecondUser | SecondUserByOnlyPresented => (Some(tournamentMatch.secondUserId), MatchStatus.Completed, winner)
          case Draw => (None, MatchStatus.Completed, winner)
          case Cancelled => (None, MatchStatus.Cancelled, winner)
          case Undefined => (None, MatchStatus.Cancelled, winner)
        }
      }
    } yield rowsUpdated == 1
  }
}
