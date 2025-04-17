package models.component

import models.StreamerEvent
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

import java.time.Instant

trait StreamerEventComponent {
  self: YtStreamerComponent =>
  
  protected val profile: JdbcProfile
  import profile.api._

  class StreamerEventsTable(tag: Tag) extends Table[StreamerEvent](tag, "streamer_events") {
    def eventId = column[Int]("event_id", O.PrimaryKey, O.AutoInc)
    def channelId = column[String]("channel_id")
    def eventName = column[String]("event_name")
    def eventDescription = column[Option[String]]("event_description")
    def eventType = column[String]("event_type")
    def currentConfidenceAmount = column[Long]("current_confidence_amount", O.Default(0L))
    def isActive = column[Boolean]("is_active", O.Default(true))
    def startTime = column[Instant]("start_time")
    def endTime = column[Option[Instant]]("end_time")
    def createdAt = column[Instant]("created_at", O.Default(Instant.now()))
    def updatedAt = column[Instant]("updated_at", O.Default(Instant.now()))
    
    def streamerFk = foreignKey("fk_streamer_events_channel", channelId, ytStreamersTable)(_.channelId)
    
    def * = (
      eventId.?,
      channelId,
      eventName,
      eventDescription,
      eventType,
      currentConfidenceAmount,
      isActive,
      startTime,
      endTime,
      createdAt.?,
      updatedAt.?
    ) <> ((StreamerEvent.apply _).tupled, StreamerEvent.unapply)
  }

  val streamerEventsTable = TableQuery[StreamerEventsTable]
  
  def getCurrentConfidenceAmountAction(eventID: Int): DBIO[Option[Long]] = {
    streamerEventsTable
      .filter(s => s.eventId === eventID)
      .map(_.currentConfidenceAmount)
      .result
      .headOption
  }
}
