package models.repository

import models.YtUser
import models.component.{UserComponent, YtUserComponent}
import models.repository.UserRepository
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class YtUserRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  userRepository: UserRepository
)(implicit ec: ExecutionContext) extends YtUserComponent with UserComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*
  


  /**
   * Create a YouTube user with full information
   */
  def createFull(ytUser: YtUser): Future[YtUser] = db.run {
    val now = Instant.now()
    val userToSave = ytUser.copy(createdAt = now, updatedAt = now)

    (ytUsersTable += userToSave).map(_ => userToSave)
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