package models.repository

import models.{EventPoll, PollOption, StreamerEvent}
import models.component.{EventPollComponent, PollOptionComponent, StreamerEventComponent, UserComponent, YtStreamerComponent}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EventPollRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  streamerEventRepository: StreamerEventRepository
)(implicit ec: ExecutionContext) extends EventPollComponent with PollOptionComponent with StreamerEventComponent with YtStreamerComponent with UserComponent{
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._
  
  /**
   * Create a new poll for an event
   */
  def create(eventPoll: EventPoll): Future[EventPoll] = db.run {
    createAction(eventPoll)
  }

  def createAction(eventPoll: EventPoll): DBIO[EventPoll] =  {
    (eventPollsTable returning eventPollsTable.map(_.pollId)
      into ((poll, id) => poll.copy(pollId = Some(id)))
      ) += eventPoll
  }
  def getByIdAction(pollId: Int): DBIO[Option[EventPoll]] =
    eventPollsTable.filter(_.pollId === pollId).result.headOption
  
  /**
   * Get a poll by ID
   */
  def getById(pollId: Int): Future[Option[EventPoll]] = db.run {
    getByIdAction(pollId)
  }
  
  /**
   * Get all polls for an event
   */
  def getByEventId(eventId: Int): Future[Seq[EventPoll]] = db.run {
    getByEventIdAction(eventId)
  }
  def getByEventIdAction(eventId: Int): DBIO[Seq[EventPoll]] =
    eventPollsTable.filter(_.eventId === eventId).result

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
  
  /**
   * Set the winner option for a poll
   */
  def setWinnerOption(pollId: Int, optionId: Int): Future[Int] = db.run {
    eventPollsTable
      .filter(_.pollId === pollId)
      .map(_.winnerOptionId)
      .update(Some(optionId))
  }
  
  // Get table query for use by other repositories
  def getTableQuery = eventPollsTable

  def getMostRecentActiveEvent(channelId: String): Future[Option[StreamerEvent]] =
    streamerEventRepository.getMostRecentActiveEvent(channelId)

  def getOverallMostRecentActiveEvent: Future[Option[StreamerEvent]] =
    streamerEventRepository.getOverallMostRecentActiveEvent

  def getEventByIdAction(eventId: Int): DBIO[Option[StreamerEvent]] =
    streamerEventRepository.getByIdAction(eventId)

  def getCurrentConfidenceAmountAction(eventID: Int): DBIO[Option[Int]] =
    streamerEventRepository.getCurrentConfidenceAmountAction(eventID)

  def updateCurrentConfidenceAmount(eventId: Int, newAmount: Int): DBIO[Int] =
    streamerEventRepository.updateCurrentConfidenceAmount(eventId, newAmount)
}
