package models.component

import models.YtStreamer
import models.component.UserComponent
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

trait YtStreamerComponent {
  self: UserComponent =>
  
  protected val profile: JdbcProfile
  import profile.api.*

  class YtStreamersTable(tag: Tag) extends Table[YtStreamer](tag, "yt_streamer") {
    def channelId = column[String]("channel_id", O.PrimaryKey)
    def ownerUserId = column[Long]("onwer_user_id")
    def currentBalanceNumber = column[Int]("current_balance_number", O.Default(0))
    
    def userFk = foreignKey("fk_yt_streamer_with_users", ownerUserId, usersTable)(_.userId)
    
    def * = (channelId, ownerUserId, currentBalanceNumber) <> ((YtStreamer.apply _).tupled, YtStreamer.unapply)
  }

  val ytStreamersTable = TableQuery[YtStreamersTable]
}