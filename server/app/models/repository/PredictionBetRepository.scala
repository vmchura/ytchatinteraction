package models.repository

import models.PredictionBet
import models.component.PredictionBetComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PredictionBetRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends PredictionBetComponent {

  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  /**
   * Inserts or replaces the prediction for (user, matchup). A user has at most
   * one prediction per matchup, so a second save overwrites the previous one.
   * Delete-then-insert keeps the upsert portable across PostgreSQL and H2.
   */
  def upsert(userId: Long, matchupId: Long, ranking: Seq[String]): Future[PredictionBet] = db.run {
    upsertAction(userId, matchupId, ranking).transactionally
  }

  def upsertAction(userId: Long, matchupId: Long, ranking: Seq[String]): DBIO[PredictionBet] = {
    for {
      _ <- predictionBetsTable.filter(b => b.userId === userId && b.matchupId === matchupId).delete
      created <- (predictionBetsTable returning predictionBetsTable.map(_.id) into ((b, id) => b.copy(id = id))) +=
        PredictionBet(userId = userId, matchupId = matchupId, ranking = ranking)
    } yield created
  }

  def findById(id: Long): Future[Option[PredictionBet]] = db.run {
    predictionBetsTable.filter(_.id === id).result.headOption
  }

  def findByUserAndMatchup(userId: Long, matchupId: Long): Future[Option[PredictionBet]] = db.run {
    findByUserAndMatchupAction(userId, matchupId)
  }

  def findByUserAndMatchupAction(userId: Long, matchupId: Long): DBIO[Option[PredictionBet]] = {
    predictionBetsTable.filter(b => b.userId === userId && b.matchupId === matchupId).result.headOption
  }

  def findByMatchup(matchupId: Long): Future[Seq[PredictionBet]] = db.run {
    findByMatchupAction(matchupId)
  }

  def findByMatchupAction(matchupId: Long): DBIO[Seq[PredictionBet]] = {
    predictionBetsTable.filter(_.matchupId === matchupId).result
  }

  def findByUserAndMatchupIds(userId: Long, matchupIds: Seq[Long]): Future[Seq[PredictionBet]] = db.run {
    predictionBetsTable
      .filter(b => b.userId === userId && b.matchupId.inSet(matchupIds))
      .result
  }

  def deleteByUserAndMatchup(userId: Long, matchupId: Long): Future[Int] = db.run {
    predictionBetsTable.filter(b => b.userId === userId && b.matchupId === matchupId).delete
  }

  def deleteById(id: Long): Future[Boolean] = db.run {
    predictionBetsTable.filter(_.id === id).delete.map(_ > 0)
  }
}
