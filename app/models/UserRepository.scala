package models

import javax.inject.{ Inject, Singleton }
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ Future, ExecutionContext }

/**
 * A repository for users.
 *
 * @param dbConfigProvider The Play db config provider. Play will inject this for you.
 */
@Singleton
class UserRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  // We want the JdbcProfile for this provider
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  // These imports are important, the first one brings db into scope, which will let you do the actual db operations.
  // The second one brings the Slick DSL into scope, which lets you define the table and other queries.
  import dbConfig._
  import profile.api._

  /**
   * Here we define the table. It will have a name of users
   */
  private class UsersTable(tag: Tag) extends Table[User](tag, "users") {

    /** The ID column, which is the primary key, and auto incremented */
    def userId = column[Long]("user_id", O.PrimaryKey, O.AutoInc)

    /** The user name column */
    def userName = column[String]("user_name")

    /**
     * This is the tables default "projection".
     *
     * It defines how the columns are converted to and from the User object.
     *
     * In this case, we are simply passing the userId and userName parameters to the User case classes
     * apply and unapply methods.
     */
    def * = (userId, userName) <> ((User.apply _).tupled, User.unapply)
  }

  /**
   * The starting point for all queries on the users table.
   */
  private val users = TableQuery[UsersTable]

  /**
   * Create a user with the given name.
   *
   * This is an asynchronous operation, it will return a future of the created user, which can be used to obtain the
   * id for that user.
   */
  def create(userName: String): Future[User] = db.run {
    // We create a projection of just the userName column, since we're not inserting a value for the id column
    (users.map(u => u.userName)
      // Now define it to return the id, because we want to know what id was generated for the user
      returning users.map(_.userId)
      // And we define a transformation for the returned value, which combines our original parameters with the
      // returned id
      into ((userName, userId) => User(userId, userName))
      // And finally, insert the user into the database
      ) += userName
  }

  /**
   * List all the users in the database.
   */
  def list(): Future[Seq[User]] = db.run {
    users.result
  }
  
  /**
   * Get a user by id.
   */
  def getById(id: Long): Future[Option[User]] = db.run {
    users.filter(_.userId === id).result.headOption
  }
  
  /**
   * Get a user by username.
   */
  def getByUsername(username: String): Future[Option[User]] = db.run {
    users.filter(_.userName === username).result.headOption
  }
}