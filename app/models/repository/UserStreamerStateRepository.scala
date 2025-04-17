package models.repository

import models.component.{UserComponent, UserStreamerStateComponent, YtStreamerComponent}
import models.repository.UserRepository
import models.UserStreamerState
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserStreamerStateRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  userRepository: UserRepository,
  ytStreamerRepository: YtStreamerRepository
)(implicit ec: ExecutionContext) extends UserStreamerStateComponent with UserComponent with YtStreamerComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*
  
  // No need to override tables since inheritance already provides access

  def createAction(userId: Long, streamerChannelId: String, currentBalanceNumber: Int = 0): DBIO[UserStreamerState] = {
    (userStreamerStateTable.map(s => (s.userId, s.streamerChannelId, s.currentBalanceNumber))
      += (userId, streamerChannelId, currentBalanceNumber)).map(_ => 
        UserStreamerState(userId, streamerChannelId, currentBalanceNumber))
  }
  
  def create(userId: Long, streamerChannelId: String, currentBalanceNumber: Int = 0): Future[UserStreamerState] = db.run {
    createAction(userId, streamerChannelId, currentBalanceNumber)
  }

  def list(): Future[Seq[UserStreamerState]] = db.run {
    userStreamerStateTable.result
  }
  
  def getByUserId(userId: Long): Future[Seq[UserStreamerState]] = db.run {
    userStreamerStateTable.filter(_.userId === userId).result
  }
  
  def getByStreamerChannelId(channelId: String): Future[Seq[UserStreamerState]] = db.run {
    userStreamerStateTable.filter(_.streamerChannelId === channelId).result
  }
  
  def existsAction(userId: Long, streamerChannelId: String): DBIO[Boolean] = {
    userStreamerStateTable
      .filter(s => s.userId === userId && s.streamerChannelId === streamerChannelId)
      .exists
      .result
  }
  
  def exists(userId: Long, streamerChannelId: String): Future[Boolean] = db.run {
    existsAction(userId, streamerChannelId)
  }
  
  def updateBalance(userId: Long, streamerChannelId: String, newBalance: Int): Future[Int] = db.run {
    userStreamerStateTable
      .filter(s => s.userId === userId && s.streamerChannelId === streamerChannelId)
      .map(_.currentBalanceNumber)
      .update(newBalance)
  }
  
  def incrementBalanceAction(userId: Long, streamerChannelId: String, amount: Int = 1): DBIO[Int] = {
    val userStreamerFilter = userStreamerStateTable
      .filter(s => s.userId === userId && s.streamerChannelId === streamerChannelId)
      .map(_.currentBalanceNumber)
    for {
      current_amount <- userStreamerFilter.result.headOption
      updated_value <- userStreamerFilter.update(current_amount.getOrElse(0) + amount)
    } yield updated_value
  }
  
  def incrementBalance(userId: Long, streamerChannelId: String, amount: Int = 1): Future[Int] = {
    db.run(incrementBalanceAction(userId, streamerChannelId, amount).transactionally)
  }
  

  
  def getBalance(userId: Long, streamerChannelId: String): Future[Option[Int]] = {
    db.run(getUserStreamerBalanceAction(userId, streamerChannelId))
  }
  
  def delete(userId: Long, streamerChannelId: String): Future[Int] = db.run {
    userStreamerStateTable
      .filter(s => s.userId === userId && s.streamerChannelId === streamerChannelId)
      .delete
  }
  
  def deleteByUserId(userId: Long): Future[Int] = db.run {
    userStreamerStateTable.filter(_.userId === userId).delete
  }
  
  def deleteByStreamerChannelId(channelId: String): Future[Int] = db.run {
    userStreamerStateTable.filter(_.streamerChannelId === channelId).delete
  }

  def updateStreamerBalanceAction(userId: Long, streamerChannelId: String, newAmount: Int): DBIO[Int] = {
    for {
      rows <- userStreamerStateTable
        .filter(s => s.userId === userId && s.streamerChannelId === streamerChannelId).map(_.currentBalanceNumber)
        .update(newAmount)
    } yield {
      rows
    }
  }
  def getUserStreamerBalanceAction(userId: Long, streamerChannelId: String): DBIO[Option[Int]] = {
    userStreamerStateTable
      .filter(s => s.userId === userId && s.streamerChannelId === streamerChannelId)
      .map(_.currentBalanceNumber)
      .result
      .headOption
  }
}