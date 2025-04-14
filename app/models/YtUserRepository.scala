package models

import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import java.time.Instant

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
  
  /**
   * Create a new YouTube user with minimal information
   */
  def create(userChannelId: String, userId: Long): Future[YtUser] = db.run {
    val now = Instant.now()
    val ytUser = YtUser(
      userChannelId = userChannelId,
      userId = userId,
      createdAt = now,
      updatedAt = now
    )
    
    (ytUsersTable returning ytUsersTable.map(_.userChannelId))
      .into((_, channelId) => ytUser)
      += ytUser
  }

  /**
   * Create a YouTube user with full information
   */
  def createFull(ytUser: YtUser): Future[YtUser] = db.run {
    val now = Instant.now()
    val userToSave = ytUser.copy(createdAt = now, updatedAt = now)
    
    (ytUsersTable returning ytUsersTable.map(_.userChannelId))
      .into((_, channelId) => userToSave)
      += userToSave
  }

  /**
   * Update a YouTube user's information
   */
  def update(ytUser: YtUser): Future[Int] = {
    val userToUpdate = ytUser.copy(updatedAt = Instant.now())
    
    db.run {
      ytUsersTable.filter(_.userChannelId === ytUser.userChannelId).update(userToUpdate)
    }
  }

  /**
   * List all YouTube users
   */
  def list(): Future[Seq[YtUser]] = db.run {
    ytUsersTable.result
  }
  
  /**
   * Get a YouTube user by channel ID
   */
  def getByChannelId(channelId: String): Future[Option[YtUser]] = db.run {
    ytUsersTable.filter(_.userChannelId === channelId).result.headOption
  }
  
  /**
   * Get all YouTube accounts for a specific user
   */
  def getByUserId(userId: Long): Future[Seq[YtUser]] = db.run {
    ytUsersTable.filter(_.userId === userId).result
  }
  
  /**
   * Delete a YouTube user by channel ID
   */
  def delete(channelId: String): Future[Int] = db.run {
    ytUsersTable.filter(_.userChannelId === channelId).delete
  }
  
  /**
   * Find a YouTube user by email
   */
  def findByEmail(email: String): Future[Option[YtUser]] = db.run {
    ytUsersTable.filter(_.email === email).result.headOption
  }
  
  /**
   * Update a YouTube user's profile
   */
  def updateProfile(
    userChannelId: String, 
    displayName: Option[String], 
    email: Option[String], 
    profileImageUrl: Option[String]
  ): Future[Int] = {
    val now = Instant.now()
    
    db.run {
      ytUsersTable
        .filter(_.userChannelId === userChannelId)
        .map(u => (u.displayName, u.email, u.profileImageUrl, u.updatedAt))
        .update((displayName, email, profileImageUrl, now))
    }
  }
  
  /**
   * Activate or deactivate a YouTube account
   */
  def setActivated(userChannelId: String, activated: Boolean): Future[Int] = {
    val now = Instant.now()
    
    db.run {
      ytUsersTable
        .filter(_.userChannelId === userChannelId)
        .map(u => (u.activated, u.updatedAt))
        .update((activated, now))
    }
  }
}