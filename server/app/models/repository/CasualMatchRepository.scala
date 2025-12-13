package models.repository

import evolutioncomplete.WinnerShared
import models.CasualMatch
import models.component.CasualMatchComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import models.StarCraftModels.*
import models.MatchStatus

trait CasualMatchRepository {
  def create(casualMatch: CasualMatch): Future[CasualMatch]
  def findById(id: Long): Future[Option[CasualMatch]]
  def findByUserId(userId: Long): Future[Seq[CasualMatch]]
  def findByRivalUserId(rivalUserId: Long): Future[Seq[CasualMatch]]
  def findByUserAndRival(userId: Long, rivalUserId: Long): Future[Seq[CasualMatch]]
  def findByStatus(userId: Long, status: MatchStatus): Future[Seq[CasualMatch]]
  def updateStatus(id: Long, status: MatchStatus): Future[Int]
  def deleteById(id: Long): Future[Int]
  def setWinner(casualMatch: CasualMatch): Future[Int]
}

@Singleton
class CasualMatchRepositoryImpl @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends CasualMatchRepository with CasualMatchComponent {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  
  import dbConfig._
  import profile.api._

  def getTableQuery = casualMatchesTable

  override def create(casualMatch: CasualMatch): Future[CasualMatch] = {
    val insertQuery = casualMatchesTable returning casualMatchesTable.map(_.id) into ((casualMatch, id) => casualMatch.copy(id = id))
    db.run(insertQuery += casualMatch)
  }

  override def findById(id: Long): Future[Option[CasualMatch]] = {
    db.run(casualMatchesTable.filter(_.id === id).result.headOption)
  }

  override def findByUserId(userId: Long): Future[Seq[CasualMatch]] = {
    db.run(casualMatchesTable.filter(_.userId === userId).sortBy(_.createdAt.desc).result)
  }

  override def findByRivalUserId(rivalUserId: Long): Future[Seq[CasualMatch]] = {
    db.run(casualMatchesTable.filter(_.rivalUserId === rivalUserId).sortBy(_.createdAt.desc).result)
  }

  override def findByUserAndRival(userId: Long, rivalUserId: Long): Future[Seq[CasualMatch]] = {
    db.run(casualMatchesTable.filter(m => m.userId === userId && m.rivalUserId === rivalUserId).sortBy(_.createdAt.desc).result)
  }

  override def findByStatus(userId: Long, status: MatchStatus): Future[Seq[CasualMatch]] = {
    db.run(casualMatchesTable.filter(m => m.userId === userId && m.status === status).sortBy(_.createdAt.desc).result)
  }

  override def updateStatus(id: Long, status: MatchStatus): Future[Int] = {
    db.run(casualMatchesTable.filter(_.id === id).map(_.status).update(status))
  }

  override def deleteById(id: Long): Future[Int] = {
    db.run(casualMatchesTable.filter(_.id === id).delete)
  }
  override def setWinner(casualMatch: CasualMatch): Future[Int] = {
    db.run(casualMatchesTable.filter(_.id === casualMatch.id).map(cm => (cm.winnerUserId, cm.status)).update((casualMatch.winnerUserId, MatchStatus.Completed)))
  }
}
