package models

import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class YtStreamerRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  userRepository: UserRepository
)(implicit ec: ExecutionContext) extends YtStreamerComponent with UserComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._
  

  def create(channelId: String, ownerUserId: Long, currentBalanceNumber: Int = 0): Future[YtStreamer] = db.run {
    (ytStreamersTable.map(s => (s.channelId, s.ownerUserId, s.currentBalanceNumber))
      += (channelId, ownerUserId, currentBalanceNumber)).map(_ => 
        YtStreamer(channelId, ownerUserId, currentBalanceNumber))
  }

  def list(): Future[Seq[YtStreamer]] = db.run {
    ytStreamersTable.result
  }
  
  def getByChannelId(channelId: String): Future[Option[YtStreamer]] = db.run {
    ytStreamersTable.filter(_.channelId === channelId).result.headOption
  }
  
  def getByOwnerUserId(ownerUserId: Long): Future[Seq[YtStreamer]] = db.run {
    ytStreamersTable.filter(_.ownerUserId === ownerUserId).result
  }
  
  def delete(channelId: String): Future[Int] = db.run {
    ytStreamersTable.filter(_.channelId === channelId).delete
  }
  
  def update(ytStreamer: YtStreamer): Future[Int] = db.run {
    ytStreamersTable.filter(_.channelId === ytStreamer.channelId)
      .map(s => (s.ownerUserId, s.currentBalanceNumber))
      .update((ytStreamer.ownerUserId, ytStreamer.currentBalanceNumber))
  }
  
  def updateBalance(channelId: String, newBalance: Int): Future[Int] = db.run {
    ytStreamersTable
      .filter(_.channelId === channelId)
      .map(_.currentBalanceNumber)
      .update(newBalance)
  }
  
  def incrementBalance(channelId: String, amount: Int = 1): Future[Int] = {
    val streamerFilter = ytStreamersTable.filter(_.channelId === channelId).map(_.currentBalanceNumber)
    val transactional = (for {
      current_amount <- streamerFilter.result.headOption
      updated_value <- streamerFilter.update(current_amount.getOrElse(0) + amount)
    } yield updated_value).transactionally
    
    db.run(transactional)
  }
  
  // Get table query for use by other repositories (like UserStreamerState)
  def getTableQuery = ytStreamersTable
}