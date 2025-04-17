package models.component

import models.component.UserComponent
import models.UserStreamerState
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

trait UserStreamerStateComponent {
  self: UserComponent with YtStreamerComponent =>
  
  protected val profile: JdbcProfile
  import profile.api.*

  class UserStreamerStateTable(tag: Tag) extends Table[UserStreamerState](tag, "user_streamer_state") {
    def userId = column[Long]("user_id")
    def streamerChannelId = column[String]("streamer_channel_id")
    def currentBalanceNumber = column[Int]("current_balance_number")
    
    def pk = primaryKey("pk_user_streamer_state", (userId, streamerChannelId))
    def userFk = foreignKey("fk_user_streamer_state_with_users", userId, usersTable)(_.userId)
    def streamerFk = foreignKey("fk_user_streamer_state_with_streamer", streamerChannelId, ytStreamersTable)(_.channelId)
    
    def * = (userId, streamerChannelId, currentBalanceNumber) <> ((UserStreamerState.apply _).tupled, UserStreamerState.unapply)
  }

  val userStreamerStateTable = TableQuery[UserStreamerStateTable]

  def getUserStreamerBalanceAction(userId: Long, streamerChannelId: String): DBIO[Option[Int]] = {
    userStreamerStateTable
      .filter(s => s.userId === userId && s.streamerChannelId === streamerChannelId)
      .map(_.currentBalanceNumber)
      .result
      .headOption
  }
  
}