package models

import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class YtUserRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  userRepository: UserRepository
)(implicit ec: ExecutionContext) extends YtUserComponent with UserComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._
  
  def create(userChannelId: String, userId: Long): Future[YtUser] = db.run {
    (ytUsersTable.map(y => (y.userChannelId, y.userId))
      += (userChannelId, userId)).map(_ => YtUser(userChannelId, userId))
  }

  def list(): Future[Seq[YtUser]] = db.run {
    ytUsersTable.result
  }
  
  def getByChannelId(channelId: String): Future[Option[YtUser]] = db.run {
    ytUsersTable.filter(_.userChannelId === channelId).result.headOption
  }
  
  def getByUserId(userId: Long): Future[Seq[YtUser]] = db.run {
    ytUsersTable.filter(_.userId === userId).result
  }
  
  def delete(channelId: String): Future[Int] = db.run {
    ytUsersTable.filter(_.userChannelId === channelId).delete
  }
}