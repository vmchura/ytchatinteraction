package models.repository

import models.EventPoll
import models.component.{EventPollComponent, StreamerEventComponent, UserComponent, YtStreamerComponent}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EventPollRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  streamerEventRepository: StreamerEventRepository
)(implicit ec: ExecutionContext) extends EventPollComponent with StreamerEventComponent with YtStreamerComponent with UserComponent{
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._
  
  /**
   * Create a new poll for an event
   */
  def create(eventPoll: EventPoll): Future[EventPoll] = db.run {
    (eventPollsTable returning eventPollsTable.map(_.pollId)
      into ((poll, id) => poll.copy(pollId = Some(id)))
    ) += eventPoll
  }
  
  /**
   * Get a poll by ID
   */
  def getById(pollId: Int): Future[Option[EventPoll]] = db.run {
    eventPollsTable.filter(_.pollId === pollId).result.headOption
  }
  
  /**
   * Get all polls for an event
   */
  def getByEventId(eventId: Int): Future[Seq[EventPoll]] = db.run {
    eventPollsTable.filter(_.eventId === eventId).result
  }
  
  /**
   * Update a poll
   */
  def update(eventPoll: EventPoll): Future[Int] = {
    require(eventPoll.pollId.isDefined, "Cannot update poll without ID")
    
    db.run {
      eventPollsTable
        .filter(_.pollId === eventPoll.pollId.get)
        .update(eventPoll)
    }
  }
  
  /**
   * Delete a poll
   */
  def delete(pollId: Int): Future[Int] = db.run {
    eventPollsTable.filter(_.pollId === pollId).delete
  }
  
  /**
   * Delete all polls for an event
   */
  def deleteByEventId(eventId: Int): Future[Int] = db.run {
    eventPollsTable.filter(_.eventId === eventId).delete
  }
  
  // Get table query for use by other repositories
  def getTableQuery = eventPollsTable
}
