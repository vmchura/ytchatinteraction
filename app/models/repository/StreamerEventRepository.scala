package models.repository

import models.StreamerEvent
import models.component.{StreamerEventComponent, UserComponent, YtStreamerComponent}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.dbio.DBIO

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class StreamerEventRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  ytStreamerRepository: YtStreamerRepository
)(implicit ec: ExecutionContext) extends StreamerEventComponent with YtStreamerComponent with UserComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._
  
  /**
   * Create a new streamer event
   */
  def create(streamerEvent: StreamerEvent): Future[StreamerEvent] = {
    val now = Instant.now()
    val eventWithTimestamps = streamerEvent.copy(
      createdAt = Some(now),
      updatedAt = Some(now)
    )
    
    db.run(
      (streamerEventsTable returning streamerEventsTable.map(_.eventId)
        into ((event, id) => event.copy(eventId = Some(id)))
      ) += eventWithTimestamps
    )
  }
  
  /**
   * Get an event by ID
   */
  def getById(eventId: Int): Future[Option[StreamerEvent]] = db.run {
    streamerEventsTable.filter(_.eventId === eventId).result.headOption
  }
  
  /**
   * Get all events for a streamer
   */
  def getByChannelId(channelId: String): Future[Seq[StreamerEvent]] = db.run {
    streamerEventsTable.filter(_.channelId === channelId).result
  }
  
  /**
   * Get all active events for a streamer
   */
  def getActiveByChannelId(channelId: String): Future[Seq[StreamerEvent]] = db.run {
    streamerEventsTable
      .filter(e => e.channelId === channelId && e.isActive === true)
      .result
  }
  
  /**
   * Get all active events
   */
  def getAllActive(): Future[Seq[StreamerEvent]] = db.run {
    streamerEventsTable.filter(_.isActive === true).result
  }

  /**
   * Get all active events
   */
  def list(): Future[Seq[StreamerEvent]] = db.run {
    streamerEventsTable.result
  }
  /**
   * Update an event
   */
  def update(streamerEvent: StreamerEvent): Future[Int] = {
    require(streamerEvent.eventId.isDefined, "Cannot update event without ID")
    
    val eventWithUpdatedTimestamp = streamerEvent.copy(
      updatedAt = Some(Instant.now())
    )
    
    db.run {
      streamerEventsTable
        .filter(_.eventId === eventWithUpdatedTimestamp.eventId.get)
        .update(eventWithUpdatedTimestamp)
    }
  }
  
  /**
   * End an event (set isActive to false and set end time)
   */
  def endEvent(eventId: Int): Future[Int] = {
    val now = Instant.now()
    
    db.run {
      streamerEventsTable
        .filter(e => e.eventId === eventId && e.isActive === true)
        .map(e => (e.isActive, e.endTime, e.updatedAt))
        .update((false, Some(now), now))
    }
  }
  
  /**
   * Delete an event
   */
  def delete(eventId: Int): Future[Int] = db.run {
    streamerEventsTable.filter(_.eventId === eventId).delete
  }
  
  /**
   * Get the most recent active event for a streamer
   */
  def getMostRecentActiveEvent(channelId: String): Future[Option[StreamerEvent]] = db.run {
    streamerEventsTable
      .filter(e => e.channelId === channelId && e.isActive === true)
      .sortBy(_.startTime.desc)
      .result
      .headOption
  }

  def getOverallMostRecentActiveEvent: Future[Option[StreamerEvent]] = db.run {
    streamerEventsTable
      .filter(e => e.isActive === true)
      .sortBy(_.startTime.desc)
      .result
      .headOption
  }

  /**
   * Add to the current confidence amount for an event
   */
  def addToCurrentConfidenceAmount(eventId: Int, amountToAdd: Long): Future[Int] = {
    val query = for {
      event <- streamerEventsTable if event.eventId === eventId
    } yield (event.currentConfidenceAmount, event.updatedAt)
    
    val action = query.result.head.flatMap { case (currentAmount, _) =>
      val newAmount = currentAmount + amountToAdd
      query.update((newAmount, Instant.now()))
    }
    
    db.run(action)
  }
  // Get table query for use by other repositories
  def getTableQuery = streamerEventsTable
}
