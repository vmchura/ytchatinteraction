package models.repository

import models.PredictionBet
import models.component.{PredictionBetComponent, PredictionMatchupComponent}
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PredictionBetRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends PredictionBetComponent with PredictionMatchupComponent {

  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  def create(bet: PredictionBet): Future[PredictionBet] = db.run {
    createAction(bet)
  }

  def createAction(bet: PredictionBet): DBIO[PredictionBet] = {
    (predictionBetsTable returning predictionBetsTable.map(_.id) into ((b, id) => b.copy(id = id))) += bet
  }

  def findById(id: Long): Future[Option[PredictionBet]] = db.run {
    predictionBetsTable.filter(_.id === id).result.headOption
  }

  def findByUserAndMatchup(userId: Long, matchupId: Long): Future[Seq[PredictionBet]] = db.run {
    predictionBetsTable.filter(b => b.userId === userId && b.matchupId === matchupId).result
  }

  def findByMatchup(matchupId: Long): Future[Seq[PredictionBet]] = db.run {
    findByMatchupAction(matchupId)
  }

  def findByMatchupAction(matchupId: Long): DBIO[Seq[PredictionBet]] = {
    predictionBetsTable.filter(_.matchupId === matchupId).result
  }

  def findByUserAndEvent(userId: Long, eventIds: Seq[Long]): Future[Seq[PredictionBet]] = db.run {
    for {
      matchupIds <- predictionMatchupsTable.filter(_.eventId.inSet(eventIds)).map(_.id).result
      bets <- predictionBetsTable
        .filter(b => b.userId === userId && b.matchupId.inSet(matchupIds))
        .result
    } yield bets
  }

  def findTotalBetByUserForMatchup(userId: Long, matchupId: Long): Future[Int] = db.run {
    predictionBetsTable
      .filter(b => b.userId === userId && b.matchupId === matchupId)
      .map(_.pointsBet)
      .sum
      .result
      .map(_.getOrElse(0))
  }

  def findTotalBetByUserForMatchupAction(userId: Long, matchupId: Long): DBIO[Int] = {
    predictionBetsTable
      .filter(b => b.userId === userId && b.matchupId === matchupId)
      .map(_.pointsBet)
      .sum
      .result
      .map(_.getOrElse(0))
  }

  def deleteByUserAndMatchup(userId: Long, matchupId: Long): Future[Int] = db.run {
    predictionBetsTable.filter(b => b.userId === userId && b.matchupId === matchupId).delete
  }

  def deleteById(id: Long): Future[Boolean] = db.run {
    predictionBetsTable.filter(_.id === id).delete.map(_ > 0)
  }

  def findByUserAndMatchupIds(userId: Long, matchupIds: Seq[Long]): Future[Seq[PredictionBet]] = db.run {
    predictionBetsTable
      .filter(b => b.userId === userId && b.matchupId.inSet(matchupIds))
      .result
  }
}
