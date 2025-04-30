package models.component

import models.UserStreamerStateLog
import slick.jdbc.JdbcProfile
import java.time.Instant

trait UserStreamerStateLogComponent {
  protected val profile: JdbcProfile
  import profile.api._

  class UserStreamerStateLogTable(tag: Tag) extends Table[UserStreamerStateLog](tag, "user_streamer_state_log") {
    def logId = column[Int]("log_id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def eventID = column[Int]("event_id")
    def amountTransferred = column[Int]("currency_transferred_amount")
    def logType = column[String]("log_type")
    def createdAt = column[Instant]("created_at", O.Default(Instant.now()))
    
    def * = (logId.?, userId, eventID, amountTransferred, logType, createdAt.?) <> (
      (UserStreamerStateLog.apply _).tupled, 
      UserStreamerStateLog.unapply
    )
    
  }
  
  val userStreamerStateLogTable = TableQuery[UserStreamerStateLogTable]
}
