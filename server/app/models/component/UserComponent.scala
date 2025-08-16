package models.component

import models.User
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

trait UserComponent {
  protected val profile: JdbcProfile
  import profile.api.*

  class UsersTable(tag: Tag) extends Table[User](tag, "users") {
    def userId = column[Long]("user_id", O.PrimaryKey, O.AutoInc)
    def userName = column[String]("user_name")
    def * = (userId, userName) <> ((User.apply _).tupled, User.unapply)
  }

  val usersTable = TableQuery[UsersTable]

  def updateUserAction(user: User): DBIO[Int] = {
    usersTable.filter(_.userId === user.userId)
      .map(u => u.userName)
      .update(user.userName)
  }
}