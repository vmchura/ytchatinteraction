package models

import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserStreamerStateRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  userRepository: UserRepository,
  ytStreamerRepository: YtStreamerRepository
)(implicit ec: ExecutionContext) extends UserStreamerStateComponent with UserComponent with YtStreamerComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._
  
  // No need to override tables since inheritance already provides access

  def create(userId: Long, streamerChannelId: String, currentBalanceNumber: Int = 0): Future[UserStreamerState] = db.run {
    (userStreamerStateTable.map(s => (s.userId, s.streamerChannelId, s.currentBalanceNumber))
      += (userId, streamerChannelId, currentBalanceNumber)).map(_ => 
        UserStreamerState(userId, streamerChannelId, currentBalanceNumber))
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
  
  def exists(userId: Long, streamerChannelId: String): Future[Boolean] = db.run {
    userStreamerStateTable
      .filter(s => s.userId === userId && s.streamerChannelId === streamerChannelId)
      .exists
      .result
  }
  
  def updateBalance(userId: Long, streamerChannelId: String, newBalance: Int): Future[Int] = db.run {
    userStreamerStateTable
      .filter(s => s.userId === userId && s.streamerChannelId === streamerChannelId)
      .map(_.currentBalanceNumber)
      .update(newBalance)
  }
  
  def incrementBalance(userId: Long, streamerChannelId: String, amount: Int = 1): Future[Int] = {
    val userStreamerFilter = userStreamerStateTable
      .filter(s => s.userId === userId && s.streamerChannelId === streamerChannelId)
      .map(_.currentBalanceNumber)
    val transactional = (for {
      current_amount <- userStreamerFilter.result.headOption
      updated_value <- userStreamerFilter.update(current_amount.getOrElse(0) + amount)
    } yield updated_value).transactionally
    
    db.run(transactional)
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
}