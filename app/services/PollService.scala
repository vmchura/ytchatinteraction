package services

import models.{EventPoll, PollOption}
import models.repository.{EventPollRepository, PollOptionRepository, PollVoteRepository, StreamerEventRepository, UserStreamerStateRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import slick.dbio.DBIO
/**
 * Service for retrieving polls and their options for streamer events
 */
@Singleton
class PollService @Inject()(
  streamerEventRepository: StreamerEventRepository,
  eventPollRepository: EventPollRepository,
  pollOptionRepository: PollOptionRepository,
  pollVoteRepository: PollVoteRepository,
  userStreamerStateRepository: UserStreamerStateRepository
)(implicit ec: ExecutionContext) {

  /**
   * Retrieves the poll and options for the most recent event for a specific channel
   *
   * @param channelId The channel ID to get the recent event for
   * @return A Future containing a tuple of (EventPoll, List[PollOption]) if found, None otherwise
   */
  def getPollForRecentEvent(channelId: String): Future[Option[(EventPoll, List[PollOption])]] = {
    // Step 1: Get the most recent active event for the channel
    streamerEventRepository.getMostRecentActiveEvent(channelId).flatMap {
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
    streamerEventRepository.getOverallMostRecentActiveEvent.flatMap {
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
  def transferConfidenceVoteStreamer(eventID: Int, userID: Long, streamChannelID: String, amount: Int): DBIO[Boolean] = {
    for {
      eventCurrentBalanceOption <- streamerEventRepository.getCurrentConfidenceAmountAction(eventID)
      eventCurrentBalance <- eventCurrentBalanceOption.fold(DBIO.failed(new IllegalStateException("No balance of the event found")))(DBIO.successful)
      userChannelBalanceOption <- userStreamerStateRepository.getUserStreamerBalanceAction(userID, streamChannelID)
      userChannelBalance <- userChannelBalanceOption.fold(DBIO.failed(new IllegalStateException("No balance of the user channel found")))(DBIO.successful)
      rows_update_event <- if(eventCurrentBalance + amount < 0) DBIO.failed(new IllegalStateException(""))
      else streamerEventRepository.updateCurrentConfidenceAmount(eventID, eventCurrentBalance + amount)
      rows_update_user_stream <- if(userChannelBalance + amount < 0) DBIO.failed(new IllegalStateException(""))
      else userStreamerStateRepository.updateStreamerBalanceAction(userID, streamChannelID, userChannelBalance - amount)
      operation_complete <- if(rows_update_event == 1 && rows_update_user_stream == 1) DBIO.successful(true) else DBIO.failed(new IllegalStateException("Not updated done"))
    }yield{
      operation_complete
    }
  }
}
