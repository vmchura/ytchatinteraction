package models.repository

import models.{User, UserAliasHistory}
import models.component.{UserComponent, UserAliasComponent}
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant
import scala.util.Random
import play.api.Logging

@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) 
    extends UserComponent with UserAliasComponent with Logging {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  // Expose the TableQuery for foreign key references
  def getTableQuery = usersTable

  def createAction(userName: String): DBIO[User] = {
    (usersTable.map(u => u.userName)
      returning usersTable.map(_.userId)
      into ((userName, userId) => User(userId, userName))
    ) += userName
  }

  /**
   * Creates a user with a randomly generated StarCraft alias.
   */
  def createWithAlias(userName: String): Future[User] = {
    val action = for {
      user <- createAction(userName)
      updatedUser <- addAlias(userName, user.userId)
    } yield user
    
    db.run(action.transactionally)
  }

  def list(): Future[Seq[User]] = db.run {
    usersTable.result
  }
  
  def getById(id: Long): Future[Option[User]] = db.run {
    usersTable.filter(_.userId === id).result.headOption
  }
  
  def getByUsername(username: String): Future[Option[User]] = db.run {
    usersTable.filter(_.userName === username).result.headOption
  }
  
  def update(user: User): Future[Int] = db.run {
    updateUserAction(user)
  }
  
  def existsAction(userId: Long): DBIO[Boolean] = {
    usersTable
      .filter(_.userId === userId)
      .exists
      .result
  }

  def exists(userId: Long): Future[Boolean] = db.run {
    existsAction(userId)
  }
  
  def delete(id: Long): Future[Int] = db.run {
    usersTable.filter(_.userId === id).delete
  }
}