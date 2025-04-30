package models.repository

import models.YtStreamer
import models.component.{UserComponent, YtStreamerComponent}
import models.repository.UserRepository
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class YtStreamerRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  userRepository: UserRepository
)(implicit ec: ExecutionContext) extends YtStreamerComponent with UserComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*
  
  /**
   * Create a YtStreamer with optional owner user ID
   */
  def create(channelId: String, ownerUserId: Option[Long], currentBalanceNumber: Int = 0, channelTitle: Option[String] = None): Future[YtStreamer] = db.run {
    (ytStreamersTable.map(s => (s.channelId, s.ownerUserId, s.currentBalanceNumber, s.channelTitle))
      += (channelId, ownerUserId, currentBalanceNumber, channelTitle)).map(_ => 
        YtStreamer(channelId, ownerUserId, currentBalanceNumber, channelTitle))
  }

  /**
   * Get all streamers
   */
  def getAll(): Future[Seq[YtStreamer]] = db.run {
    ytStreamersTable.result
  }
  
  /**
   * Alias for getAll (for backward compatibility)
   */
  def list(): Future[Seq[YtStreamer]] = getAll()
  
  def getByChannelId(channelId: String): Future[Option[YtStreamer]] = db.run {
    ytStreamersTable.filter(_.channelId === channelId).result.headOption
  }
  
  def getByOwnerUserId(ownerUserId: Long): Future[Seq[YtStreamer]] = db.run {
    ytStreamersTable.filter(_.ownerUserId === Some(ownerUserId)).result
  }
  
  def delete(channelId: String): Future[Int] = db.run {
    ytStreamersTable.filter(_.channelId === channelId).delete
  }
  
  def update(ytStreamer: YtStreamer): Future[Int] = db.run {
    ytStreamersTable.filter(_.channelId === ytStreamer.channelId)
      .map(s => (s.ownerUserId, s.currentBalanceNumber, s.channelTitle))
      .update((ytStreamer.ownerUserId, ytStreamer.currentBalanceNumber, ytStreamer.channelTitle))
  }
  
  /**
   * Update the owner user ID for a YtStreamer
   */
  def updateOwner(channelId: String, ownerUserId: Option[Long]): Future[Int] = db.run {
    ytStreamersTable
      .filter(_.channelId === channelId)
      .map(_.ownerUserId)
      .update(ownerUserId)
  }
  
  /**
   * Update the channel title for a YtStreamer
   */
  def updateChannelTitle(channelId: String, channelTitle: Option[String]): Future[Int] = db.run {
    ytStreamersTable
      .filter(_.channelId === channelId)
      .map(_.channelTitle)
      .update(channelTitle)
  }
  
  /**
   * Check if the YtStreamer already has an owner assigned
   */
  def hasOwner(channelId: String): Future[Boolean] = db.run {
    ytStreamersTable
      .filter(s => s.channelId === channelId && s.ownerUserId.isDefined)
      .exists
      .result
  }

  def updateBalanceAction(channelId: String, newBalance: Int): DBIO[Int] =
    ytStreamersTable
      .filter(_.channelId === channelId)
      .map(_.currentBalanceNumber)
      .update(newBalance)

  def updateBalance(channelId: String, newBalance: Int): Future[Int] = db.run {
    updateBalanceAction(channelId, newBalance)
  }
  
  def incrementBalanceAction(channelId: String, amount: Int): DBIO[Int] = {
    val streamerFilter = ytStreamersTable.filter(_.channelId === channelId).map(_.currentBalanceNumber)
    for {
      current_amount <- streamerFilter.result.headOption
      new_amount <- current_amount.fold(DBIO.failed(new IllegalStateException("Not balance found for increment")))(r => {
        if(r + amount >= 0) {
          DBIO.successful(r + amount)
        }else{
          DBIO.failed(new IllegalStateException("Negative balance for channel"))
        }
      })
      updated_value <- streamerFilter.update(new_amount)
    } yield updated_value
  }
  
  def incrementBalance(channelId: String, amount: Int): Future[Int] = {
    db.run(incrementBalanceAction(channelId, amount).transactionally)
  }
  
  
  
  def getBalance(channelId: String): Future[Option[Int]] = {
    db.run(getStreamerBalanceAction(channelId))
  }
  
  // Get table query for use by other repositories (like UserStreamerState)
  def getTableQuery = ytStreamersTable
}