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
    db.run(createAction(streamerEvent))
  }
  def createAction(streamerEvent: StreamerEvent): DBIO[StreamerEvent] = {
    val now = Instant.now()
    val eventWithTimestamps = streamerEvent.copy(
      createdAt = Some(now),
      updatedAt = Some(now)
    )

    (streamerEventsTable returning streamerEventsTable.map(_.eventId)
      into ((event, id) => event.copy(eventId = Some(id)))
      ) += eventWithTimestamps
  }

  def getByIdAction(eventId: Int): DBIO[Option[StreamerEvent]] =
    streamerEventsTable.filter(_.eventId === eventId).result.headOption
  /**
   * Get an event by ID
   */
  def getById(eventId: Int): Future[Option[StreamerEvent]] = db.run {
    getByIdAction(eventId)
  }
  
  /**
   * Get all events for a streamer
   */
  def getByChannelId(channelId: String): Future[Seq[StreamerEvent]] = db.run {
    streamerEventsTable.filter(_.channelId === channelId).result
  }
  
  /**
   * Get all active events for a streamer (isActive = true)
   */
  def getActiveByChannelId(channelId: String): Future[Seq[StreamerEvent]] = db.run {
    streamerEventsTable
      .filter(e => e.channelId === channelId && e.isActive === true)
      .result
  }
  
  /**
   * Get all events for a streamer that haven't ended yet (endTime is null)
   */
  def getActiveEventsByChannel(channelId: String): Future[Seq[StreamerEvent]] = db.run {
    streamerEventsTable
      .filter(e => e.channelId === channelId && e.endTime.isEmpty)
      .result
  }

  /**
   * Get all active events
   */
  def list(): Future[Seq[StreamerEvent]] = db.run {
    streamerEventsTable.result
  }.map(_.sortBy(_.createdAt).reverse)
  
  /**
   * Get all active events (isActive = true and endTime is null)
   */
  def getAllActiveEvents(): Future[Seq[StreamerEvent]] = db.run {
    streamerEventsTable
      .filter(e => e.isActive === true && e.endTime.isEmpty)
      .result
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
   * Stop new votes for an event (set isActive to false)
   */
  def endEvent(eventId: Int): Future[Int] = {
    val now = Instant.now()
    
    db.run {
      streamerEventsTable
        .filter(e => e.eventId === eventId && e.isActive === true)
        .map(e => (e.isActive, e.updatedAt))
        .update((false, now))
    }
  }
  
  /**
   * Close an event (set end time)
   */
  def closeEventAction(eventId: Int): DBIO[Int] = {
    val now = Instant.now()
      streamerEventsTable
        .filter(e => e.eventId === eventId)
        .map(e => (e.endTime, e.updatedAt))
        .update((Some(now), now))
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
  def updateCurrentConfidenceAmount(eventId: Int, newAmount: Int): DBIO[Int] = {
    for{
      rows <- streamerEventsTable
      .filter(_.eventId === eventId).map(_.currentConfidenceAmount)
      .update(newAmount)
    }yield{
      rows
    }
  }

  def incrementCurrentConfidenceAmount(eventId: Int, amount: Int): DBIO[Int] = {
    val streamerFilter = streamerEventsTable.filter(_.eventId === eventId).map(_.currentConfidenceAmount)
    for {
      current_amount <- streamerFilter.result.headOption
      new_amount <- current_amount.fold(DBIO.failed(new IllegalStateException("Not balance found for event")))(r => {
        if (r + amount >= 0) {
          DBIO.successful(r + amount)
        } else {
          DBIO.failed(new IllegalStateException("Negative balance for event"))
        }
      })
      updated_value <- streamerFilter.update(new_amount)
    } yield updated_value
  }

  def getCurrentConfidenceAmountAction(eventID: Int): DBIO[Option[Int]] = {
    streamerEventsTable
      .filter(s => s.eventId === eventID)
      .map(_.currentConfidenceAmount)
      .result
      .headOption
  }
  // Get table query for use by other repositories
  def getTableQuery = streamerEventsTable
}
