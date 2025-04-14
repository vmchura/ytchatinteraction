package models

import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

trait YtUserComponent {
  self: UserComponent =>
  
  protected val profile: JdbcProfile
  import profile.api._

  class YtUsersTable(tag: Tag) extends Table[YtUser](tag, "yt_users") {
    def userChannelId = column[String]("user_channel_id", O.PrimaryKey)
    def userId = column[Long]("user_id")
    
    def userFk = foreignKey("fk_yt_users_with_users", userId, usersTable)(_.userId)
    
    def * = (userChannelId, userId) <> ((YtUser.apply _).tupled, YtUser.unapply)
  }

  val ytUsersTable = TableQuery[YtUsersTable]
}