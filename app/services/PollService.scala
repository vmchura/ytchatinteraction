package services

import forms.PollForm
import models.{EventPoll, FrontalPoll, FrontalPollOption, FrontalStreamerEvent, PollOption, PollVote, StreamerEvent, UserStreamerStateLog}
import models.repository.{EventPollRepository, PollOptionRepository, PollVoteRepository, StreamerEventRepository, UserStreamerStateLogRepository, UserStreamerStateRepository, YtStreamerRepository}
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
  dbConfigProvider: DatabaseConfigProvider,
  userStreamerStateLogRepository: UserStreamerStateLogRepository,
  streamerEventRepository: StreamerEventRepository,
  ytStreamerRepository: YtStreamerRepository
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
                       confidenceAmount: Int,
                       authorChannelId: String): Future[EventPoll] = {
    db.run(registerPollVoteAction(pollId, optionId, userId, messageByChatOpt, confidenceAmount, authorChannelId).transactionally)
  }
  def registerPollVoteAction(pollId: Int,
                       optionId: Int,
                       userId: Long,
                       messageByChatOpt: Option[String],
                       confidenceAmount: Int,
                             authorChannelId: String): DBIO[EventPoll] = {
    for{
      _ <- pollVoteRepository.addVoteAction(PollVote(None, pollId, optionId, userId, messageByChatOpt, confidenceAmount), authorChannelId)
      eventPollOption <- eventPollRepository.getByIdAction(pollId)
      eventPoll <- eventPollOption.fold(DBIO.failed(new IllegalStateException("No poll found by pollID")))(e => DBIO.successful(e))
      eventOption <- eventPollRepository.getEventByIdAction(eventPoll.eventId)
      event <- eventOption.fold(DBIO.failed(new IllegalStateException("No event found by eventId")))(e => DBIO.successful(e))
      _ <- transferConfidenceVoteStreamer(event, userId, confidenceAmount, "VOTE")
    }yield{
      eventPoll
    }
  }

  def transferConfidenceVoteStreamer(event: StreamerEvent, userID: Long, amount: Int, logType: String): DBIO[Boolean] = {
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
      _ <- userStreamerStateLogRepository.createAction(UserStreamerStateLog(None, Some(userID), None, eventID, amount, logType))
    }yield{
      operation_complete
    }
  }
  def spreadPollConfidenceAction(eventID: Int, pollID: Int): DBIO[Boolean] = {
    (for{
      eventOption <- eventPollRepository.getEventByIdAction(eventID)
      event <- eventOption.fold(DBIO.failed(new IllegalStateException("No event ID?")))(DBIO.successful)
      pollOption <- eventPollRepository.getByIdAction(pollID)
      poll <- pollOption.fold(DBIO.failed(new IllegalStateException("No PollID")))(DBIO.successful)
      winnerOptionID <- poll.winnerOptionId.fold(DBIO.failed(new IllegalStateException("No winner selected?")))(DBIO.successful)
      winnerOptionOption <- pollOptionRepository.getByIdAction(winnerOptionID)
      winnerOption <- winnerOptionOption.fold(DBIO.failed(new IllegalStateException("No winner option in DB?")))(DBIO.successful)
      votes <- pollVoteRepository.getByPollIdAction(pollID)
      
      // Filter the winning votes
      winningVotes = votes.filter(singleVote => winnerOption.optionId.exists(_ == singleVote.optionId))
      
      // Use foldLeft to process transactions sequentially while maintaining transaction integrity
      transactionMessages <- winningVotes.foldLeft[DBIO[Boolean]](DBIO.successful(true)) {
        case (accDBIO, singleVote) =>
          for {
            acc <- accDBIO
            _ <- if(acc) DBIO.successful(true) else DBIO.failed(new IllegalStateException("Not paying fine"))
            paymentAmount = -(singleVote.confidenceAmount*PollOption.fromProbabilityWinRate(winnerOption.confidenceRatio)).toInt
            result <- transferConfidenceVoteStreamer(event, singleVote.userId, paymentAmount, "PAY")
          } yield acc && result
      }
      
      currentBalanceEventOption <- streamerEventRepository.getCurrentConfidenceAmountAction(eventID)
      currentBalanceEvent <- currentBalanceEventOption.fold(DBIO.failed(new IllegalStateException("No event at closing?")))(r => DBIO.successful(r))
      _ <- userStreamerStateLogRepository.createAction(UserStreamerStateLog(None, None, Some(event.channelId), eventID, -currentBalanceEvent, "RECOVER"))
      _ <- transferFromChannelToEvent(event.channelId, eventID, -currentBalanceEvent)
    }yield{
      // Logging done outside the yield to avoid potential issues
      transactionMessages
    })
  }
  def spreadPollConfidence(eventID: Int, pollID: Int): Future[Boolean] = {
    import slick.jdbc.TransactionIsolation.Serializable
    db.run(spreadPollConfidenceAction(eventID, pollID)
      .transactionally
      .withTransactionIsolation(Serializable))
      .recoverWith {
        case e: Exception => 
          println(s"Transaction failed: ${e.getMessage}")
          // You might want to add proper logging here
          Future.failed(e)
      }
  }
  def completeFrontalPoll(frontalStreamerEvent: FrontalStreamerEvent): Future[FrontalStreamerEvent] = {

    for {
      sequencePoll <- eventPollRepository.getByEventId(frontalStreamerEvent.eventId)
      pollWithOptions: Seq[Seq[PollOption]] <- Future.sequence(sequencePoll.map(eventPoll => eventPoll.pollId.fold(Future.successful(Nil))(pollID => pollOptionRepository.getByPollId(pollID))))
    }yield{
      val frontalPoll = sequencePoll.zip(pollWithOptions).flatMap{ case (eventPoll, seqOptions) =>
        eventPoll.pollId.map{ pollID =>
          FrontalPoll(pollID, eventPoll.pollQuestion, seqOptions.flatMap(pollOption => pollOption.optionId.map(optionID => FrontalPollOption(optionID, pollOption.optionText, pollOption.confidenceRatio))))  
        }
      }
      frontalStreamerEvent.copy(frontalPoll = frontalPoll)
    }
  }
  def createEventAction(event: StreamerEvent, pollForm: PollForm): DBIO[StreamerEvent] = {
    for {
      createdEvent <- streamerEventRepository.createAction(event.copy(currentConfidenceAmount = 0))
      poll = EventPoll(
        eventId = createdEvent.eventId.get,
        pollQuestion = pollForm.pollQuestion
      )
      createdPoll <- eventPollRepository.createAction(poll)
      _ <- pollOptionRepository.createMultipleAction(createdPoll.pollId.get, pollForm.options, pollForm.ratios)
      _ <- userStreamerStateLogRepository.createAction(UserStreamerStateLog(None, None, Some(event.channelId), createdEvent.eventId.get, event.currentConfidenceAmount, "CREATE"))
      _ <- transferFromChannelToEvent(event.channelId, createdEvent.eventId.get, event.currentConfidenceAmount)
    }yield{
      createdEvent
    }
  }

  def createEvent(event: StreamerEvent, pollForm: PollForm): Future[StreamerEvent] = {
    db.run(createEventAction(event, pollForm).transactionally)
  }
  def transferFromChannelToEvent(channelID: String, eventID: Int, amount: Int): DBIO[Boolean] = {
    for{
      newBalanceChannel <- ytStreamerRepository.incrementBalanceAction(channelID, -amount)
      newBalanceEvent <- streamerEventRepository.incrementCurrentConfidenceAmount(eventID, amount)
    }yield {
      newBalanceChannel >= 0 && newBalanceEvent >=0
    }
  }
  def balancePollAction(eventID: Int, pollID: Int): DBIO[Boolean] = {
    (for{
      eventOption <- eventPollRepository.getEventByIdAction(eventID)
      event <- eventOption.fold(DBIO.failed(new IllegalStateException("No event ID?")))(DBIO.successful)
      pollOption <- eventPollRepository.getByIdAction(pollID)
      poll <- pollOption.fold(DBIO.failed(new IllegalStateException("No PollID")))(DBIO.successful)
      pollOptions <- pollOptionRepository.getByPollIdAction(pollID)
      winnerRatio = pollOptions.flatMap(po => po.optionId.map(oid => oid -> PollOption.fromProbabilityWinRate(po.confidenceRatio))).toMap
      sumConfidenceByOption <- pollVoteRepository.sumConfidenceByOption(pollID)
      votes <- pollVoteRepository.getByPollIdAction(pollID)

      // Filter the winning votes

      // Use foldLeft to process transactions sequentially while maintaining transaction integrity
      transactionMessages <- votes.foldLeft[DBIO[List[String]]](DBIO.successful(List.empty)) {
        case (accDBIO, singleVote) =>
          for {
            acc <- accDBIO
            numerator = event.currentConfidenceAmount - sumConfidenceByOption.getOrElse(singleVote.optionId, 0)
            denominator = sumConfidenceByOption.getOrElse(singleVote.optionId, 0)*(winnerRatio.getOrElse(singleVote.optionId, BigDecimal(1.0)) - 1.0)
            ratio = numerator*1.0 / denominator
            newAmount = (ratio*singleVote.confidenceAmount).toInt
            returnAmount = singleVote.confidenceAmount - newAmount
            _ <- if(returnAmount>0) transferConfidenceVoteStreamer(event, singleVote.userId, -returnAmount, "BALANCE") >> pollVoteRepository.updateConfidenceAmountAction(singleVote.voteId.get, newAmount) else DBIO.successful(true)
            message = s"From ${singleVote.userId} balancing: $returnAmount"
          } yield acc :+ message
      }
    }yield{
      // Logging done outside the yield to avoid potential issues
      true
    })
  }
  def closeEvent(eventID: Int): Future[List[Boolean]] = {
    val completeCloseEvents = for{
      _ <- streamerEventRepository.closeEventAction(eventID)
      polls <- eventPollRepository.getByEventIdAction(eventID)
      transactionMessages <- polls.foldLeft[DBIO[List[Boolean]]](DBIO.successful(List.empty)) {
        case (accDBIO, singlePoll) =>
          for {
            acc <- accDBIO
            balanced <- singlePoll.pollId.fold(DBIO.successful(true))(pollID =>
              println(f"balancing PollAction: ${pollID}")
              {balancePollAction(eventID, pollID)})
          } yield acc :+ balanced
      }
    }yield{
      transactionMessages
    }
    db.run(completeCloseEvents.transactionally)
  }

  def endEvent(eventID: Int): Future[List[Boolean]] = {
    val completeCloseEvents = for {
      _ <- streamerEventRepository.endEventAction(eventID)
      polls <- eventPollRepository.getByEventIdAction(eventID)
      transactionMessages <- polls.foldLeft[DBIO[List[Boolean]]](DBIO.successful(List.empty)) {
        case (accDBIO, singlePoll) =>
          for {
            acc <- accDBIO
            balanced <- singlePoll.pollId.fold(DBIO.successful(true))(pollID =>
              println(f"balancing PollAction: ${pollID}")
              {
                balancePollAction(eventID, pollID)
              })
          } yield acc :+ balanced
      }
    } yield {
      transactionMessages
    }
    db.run(completeCloseEvents.transactionally)
  }
}
