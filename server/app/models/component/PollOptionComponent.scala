package models.component

import models.PollOption
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

trait PollOptionComponent {
  self: EventPollComponent =>
  
  protected val profile: JdbcProfile
  import profile.api._

  class PollOptionsTable(tag: Tag) extends Table[PollOption](tag, "poll_options") {
    def optionId = column[Int]("option_id", O.PrimaryKey, O.AutoInc)
    def pollId = column[Int]("poll_id")
    def optionText = column[String]("option_text")
    def confidenceRatio = column[BigDecimal]("confidence_ratio")
    
    def pollFk = foreignKey("fk_poll_options_poll", pollId, eventPollsTable)(_.pollId)
    
    def * = (optionId.?, pollId, optionText, confidenceRatio) <> ((PollOption.apply _).tupled, PollOption.unapply)
  }

  val pollOptionsTable = TableQuery[PollOptionsTable]
}
