package models.component

import models.EventPoll
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

trait EventPollComponent {
  self: StreamerEventComponent =>
  
  protected val profile: JdbcProfile
  import profile.api._

  class EventPollsTable(tag: Tag) extends Table[EventPoll](tag, "event_polls") {
    def pollId = column[Int]("poll_id", O.PrimaryKey, O.AutoInc)
    def eventId = column[Int]("event_id")
    def pollQuestion = column[String]("poll_question")
    
    def eventFk = foreignKey("fk_event_polls_event", eventId, streamerEventsTable)(_.eventId)
    
    def * = (pollId.?, eventId, pollQuestion) <> ((EventPoll.apply _).tupled, EventPoll.unapply)
  }

  val eventPollsTable = TableQuery[EventPollsTable]
}
