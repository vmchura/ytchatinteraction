package services

import models.{EventPoll, PollOption, PollVote, StreamerEvent}
import models.repository.{EventPollRepository, PollOptionRepository, PollVoteRepository, StreamerEventRepository, UserStreamerStateRepository}
import org.apache.pekko.event.EventStream
import play.api.db.slick.DatabaseConfigProvider

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile
/**
 * Service for retrieving polls and their options for streamer events
 */
@Singleton
class PollService @Inject()(
  eventPollRepository: EventPollRepository,
  pollOptionRepository: PollOptionRepository,
  pollVoteRepository: PollVoteRepository,
  userStreamerStateRepository: UserStreamerStateRepository,
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  protected val profile = dbConfig.profile

  import dbConfig._
  import profile.api._
  /**
   * Retrieves the poll and options for the most recent event for a specific channel
   *
   * @param channelId The channel ID to get the recent event for
   * @return A Future containing a tuple of (EventPoll, List[PollOption]) if found, None otherwise
   */
  def getPollForRecentEvent(channelId: String): Future[Option[(EventPoll, List[PollOption])]] = {
    // Step 1: Get the most recent active event for the channel
    eventPollRepository.getMostRecentActiveEvent(channelId).flatMap {
      case Some(event) => 
        // Step 2: Get polls for this event
        event.eventId match {
          case Some(eventID) => getPollForEvent(eventID)
          case None => Future.successful(None)
        }
      case None =>
        // No recent event found
        Future.successful(None)
    }
  }

  def getPollForRecentEventOverall: Future[Option[(EventPoll, List[PollOption])]] = {
    // Step 1: Get the most recent active event for the channel
    eventPollRepository.getOverallMostRecentActiveEvent.flatMap {
      case Some(event) =>
        // Step 2: Get polls for this event
        event.eventId match {
          case Some(eventID) => getPollForEvent(eventID)
          case None => Future.successful(None)
        }
      case None =>
        // No recent event found
        Future.successful(None)
    }
  }

  /**
   * Retrieves the poll and options for a specific event
   *
   * @param eventId The event ID
   * @return A Future containing a tuple of (EventPoll, List[PollOption]) if found, None otherwise
   */
  def getPollForEvent(eventId: Int): Future[Option[(EventPoll, List[PollOption])]] = {
    // Get polls for this event
    eventPollRepository.getByEventId(eventId).flatMap { polls =>
      if (polls.isEmpty) {
        // No polls for this event
        Future.successful(None)
      } else {
        // Get the first poll (assuming one poll per event)
        val poll = polls.head
        
        // Get options for this poll
        pollOptionRepository.getByPollId(poll.pollId.get).map { options =>
          Some((poll, options.toList))
        }
      }
    }
  }
  def registerPollVote(pollId: Int,
                       optionId: Int,
                       userId: Long,
                       messageByChatOpt: Option[String],
                       confidenceAmount: Int): Future[EventPoll] = {
    db.run(registerPollVoteAction(pollId, optionId, userId, messageByChatOpt, confidenceAmount).transactionally)
  }
  def registerPollVoteAction(pollId: Int,
                       optionId: Int,
                       userId: Long,
                       messageByChatOpt: Option[String],
                       confidenceAmount: Int): DBIO[EventPoll] = {
    for{
      _ <- pollVoteRepository.addVoteAction(PollVote(None, pollId, optionId, userId, messageByChatOpt, confidenceAmount))
      eventPollOption <- eventPollRepository.getByIdAction(pollId)
      eventPoll <- eventPollOption.fold(DBIO.failed(new IllegalStateException("No poll found by pollID")))(e => DBIO.successful(e))
      eventOption <- eventPollRepository.getEventByIdAction(eventPoll.eventId)
      event <- eventOption.fold(DBIO.failed(new IllegalStateException("No event found by eventId")))(e => DBIO.successful(e))
      _ <- transferConfidenceVoteStreamer(event, userId, confidenceAmount)
    }yield{
      eventPoll
    }
  }

  def transferConfidenceVoteStreamer(event: StreamerEvent, userID: Long, amount: Int): DBIO[Boolean] = {
    for {
      eventID <- event.eventId.fold(DBIO.failed(new IllegalStateException("Event with no ID?")))(e => DBIO.successful(e))
      eventCurrentBalanceOption <- eventPollRepository.getCurrentConfidenceAmountAction(eventID)
      eventCurrentBalance <- eventCurrentBalanceOption.fold(DBIO.failed(new IllegalStateException("No balance of the event found")))(DBIO.successful)
      userChannelBalanceOption <- userStreamerStateRepository.getUserStreamerBalanceAction(userID, event.channelId)
      userChannelBalance <- userChannelBalanceOption.fold(DBIO.failed(new IllegalStateException("No balance of the user channel found")))(DBIO.successful)
      actualTransferAmount <- DBIO.successful(if(amount == Int.MaxValue) userChannelBalance else amount)
      rows_update_event <- if(eventCurrentBalance + actualTransferAmount < 0) DBIO.failed(new IllegalStateException("Negative balance for streamer event"))
      else eventPollRepository.updateCurrentConfidenceAmount(eventID, eventCurrentBalance + actualTransferAmount)
      rows_update_user_stream <- if(userChannelBalance - actualTransferAmount < 0) DBIO.failed(new IllegalStateException("Negative amount for user balance"))
      else userStreamerStateRepository.updateStreamerBalanceAction(userID, event.channelId, userChannelBalance - actualTransferAmount)
      operation_complete <- if(rows_update_event == 1 && rows_update_user_stream == 1) DBIO.successful(true) else DBIO.failed(new IllegalStateException("Not updated done"))
    }yield{
      operation_complete
    }
  }
  def spreadPollConfidenceAction(eventID: Int, pollID: Int): DBIO[Boolean] = {
    for{
      eventOption <- eventPollRepository.getEventByIdAction(eventID)
      event <- eventOption.fold(DBIO.failed(new IllegalStateException("No event ID?")))(DBIO.successful)
      pollOption <- eventPollRepository.getByIdAction(pollID)
      poll <- pollOption.fold(DBIO.failed(new IllegalStateException("No PollID")))(DBIO.successful)
      winnerOptionID <- poll.winnerOptionId.fold(DBIO.failed(new IllegalStateException("No winner selected?")))(DBIO.successful)
      winnerOptionOption <- pollOptionRepository.getByIdAction(winnerOptionID)
      winnerOption <- winnerOptionOption.fold(DBIO.failed(new IllegalStateException("No winner option in DB?")))(DBIO.successful)
      votes <- pollVoteRepository.getByPollIdAction(pollID)
      transactions <- DBIO.sequence(votes.filter(singleVote => winnerOption.optionId.exists(_ == singleVote.optionId)).map{ singleVote =>
        transferConfidenceVoteStreamer(event, singleVote.userId, -singleVote.confidenceAmount*(1.0f/(winnerOption.confidenceRatio*(1+0.05))).toInt)
      })

    }yield{
      transactions.forall(_ == true)
    }
  }
  def spreadPollConfidence(eventID: Int, pollID: Int): Future[Boolean] = db.run(spreadPollConfidenceAction(eventID, pollID).transactionally)
}
