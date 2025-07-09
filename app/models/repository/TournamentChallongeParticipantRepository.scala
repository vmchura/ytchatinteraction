package models.repository

import models.TournamentChallongeParticipant
import models.component.TournamentChallongeParticipantComponent
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

trait TournamentChallongeParticipantRepository {

  /**
   * Creates a new tournament Challonge participant mapping
   */
  def create(tournamentChallongeParticipant: TournamentChallongeParticipant): Future[TournamentChallongeParticipant]

  /**
   * Finds a tournament Challonge participant mapping by tournament ID and user ID
   */
  def findByTournamentAndUser(tournamentId: Long, userId: Long): Future[Option[TournamentChallongeParticipant]]

  /**
   * Finds a tournament Challonge participant mapping by Challonge participant ID
   */
  def findByChallongeParticipantId(challongeParticipantId: Long): Future[Option[TournamentChallongeParticipant]]

  /**
   * Finds all tournament Challonge participant mappings for a tournament
   */
  def findByTournamentId(tournamentId: Long): Future[List[TournamentChallongeParticipant]]

  /**
   * Finds all tournament Challonge participant mappings for a Challonge tournament
   */
  def findByChallongeTournamentId(challongeTournamentId: Long): Future[List[TournamentChallongeParticipant]]

  /**
   * Updates an existing tournament Challonge participant mapping
   */
  def update(tournamentChallongeParticipant: TournamentChallongeParticipant): Future[TournamentChallongeParticipant]

  /**
   * Deletes a tournament Challonge participant mapping
   */
  def delete(id: Long): Future[Boolean]

  /**
   * Deletes all tournament Challonge participant mappings for a tournament
   */
  def deleteByTournamentId(tournamentId: Long): Future[Int]
}

@Singleton
class TournamentChallongeParticipantRepositoryImpl @Inject()(
                                                              dbConfigProvider: play.api.db.slick.DatabaseConfigProvider
                                                            )(implicit ec: ExecutionContext)
  extends TournamentChallongeParticipantRepository
    with TournamentChallongeParticipantComponent {

  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile

  import dbConfig.*
  import profile.api.*

  override def create(tournamentChallongeParticipant: TournamentChallongeParticipant): Future[TournamentChallongeParticipant] = {
    val insertQuery = tournamentChallongeParticipantsTable returning tournamentChallongeParticipantsTable.map(_.id) into ((participant, id) => participant.copy(id = id))
    db.run(insertQuery += tournamentChallongeParticipant)
  }

  override def findByTournamentAndUser(tournamentId: Long, userId: Long): Future[Option[TournamentChallongeParticipant]] = {
    val query = tournamentChallongeParticipantsTable
      .filter(p => p.tournamentId === tournamentId && p.userId === userId)
      .result
      .headOption
    db.run(query)
  }

  override def findByChallongeParticipantId(challongeParticipantId: Long): Future[Option[TournamentChallongeParticipant]] = {
    val query = tournamentChallongeParticipantsTable
      .filter(_.challongeParticipantId === challongeParticipantId)
      .result
      .headOption
    db.run(query)
  }

  override def findByTournamentId(tournamentId: Long): Future[List[TournamentChallongeParticipant]] = {
    val query = tournamentChallongeParticipantsTable
      .filter(_.tournamentId === tournamentId)
      .result
    db.run(query).map(_.toList)
  }

  override def findByChallongeTournamentId(challongeTournamentId: Long): Future[List[TournamentChallongeParticipant]] = {
    val query = tournamentChallongeParticipantsTable
      .filter(_.challongeTournamentId === challongeTournamentId)
      .result
    db.run(query).map(_.toList)
  }

  override def update(tournamentChallongeParticipant: TournamentChallongeParticipant): Future[TournamentChallongeParticipant] = {
    val updatedParticipant = tournamentChallongeParticipant.copy(updatedAt = Instant.now())
    val updateQuery = tournamentChallongeParticipantsTable
      .filter(_.id === updatedParticipant.id)
      .update(updatedParticipant)

    db.run(updateQuery).map(_ => updatedParticipant)
  }

  override def delete(id: Long): Future[Boolean] = {
    val deleteQuery = tournamentChallongeParticipantsTable.filter(_.id === id).delete
    db.run(deleteQuery).map(_ > 0)
  }

  override def deleteByTournamentId(tournamentId: Long): Future[Int] = {
    val deleteQuery = tournamentChallongeParticipantsTable.filter(_.tournamentId === tournamentId).delete
    db.run(deleteQuery)
  }
}
