package models.repository

import models.User
import models.component.UserComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) extends UserComponent {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  // Expose the TableQuery for foreign key references
  def getTableQuery = usersTable

  def create(userName: String): Future[User] = db.run {
    (usersTable.map(u => u.userName)
      returning usersTable.map(_.userId)
      into ((userName, userId) => User(userId, userName))
    ) += userName
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
    usersTable.filter(_.userId === user.userId)
      .map(u => u.userName)
      .update(user.userName)
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