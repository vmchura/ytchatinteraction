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
  

  def create(channelId: String, ownerUserId: Long): Future[YtStreamer] = db.run {
    (ytStreamersTable.map(s => (s.channelId, s.ownerUserId))
      += (channelId, ownerUserId)).map(_ => YtStreamer(channelId, ownerUserId))
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
      .map(_.ownerUserId)
      .update(ytStreamer.ownerUserId)
  }
  
  // Get table query for use by other repositories (like UserStreamerState)
  def getTableQuery = ytStreamersTable
}