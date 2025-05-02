package models.repository

import com.sun.java.accessibility.util.EventID
import models.PollVote
import models.component.*
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class PollVoteRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  pollOptionRepository: PollOptionRepository,
  userRepository: UserRepository
)(implicit ec: ExecutionContext) extends PollVoteComponent with PollOptionComponent with EventPollComponent 
  with StreamerEventComponent with UserComponent with YtStreamerComponent with UserStreamerStateComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._
  
  
  def addVoteAction(pollVote: PollVote, authorChannelId: String): DBIO[PollVote] = {
    val maximum_vote = if(pollVote.confidenceAmount == Int.MaxValue) {
      for {
        currentBalanceOption <- userStreamerStateTable
          .filter(s => s.userId === pollVote.userId && s.streamerChannelId === authorChannelId)
          .map(_.currentBalanceNumber)
          .result
          .headOption
        currentBalance <- currentBalanceOption.fold(DBIO.failed(new IllegalStateException("Balance not found when all in")))(r => DBIO.successful(r))
      } yield {
        currentBalance
      }

    } else DBIO.successful(pollVote.confidenceAmount)

    for {
      max_vote <- maximum_vote
      voteWithTimestamp = pollVote.copy(
        createdAt = Some(Instant.now()),
        confidenceAmount=max_vote
      )
      voteAction <- (pollVotesTable returning pollVotesTable.map(_.voteId)
        into ((vote, id) => vote.copy(voteId = Some(id)))
        ) += voteWithTimestamp
    }yield{
      voteAction
    }
  }
  /**
   * Create a new vote for a poll option and update the event's confidence amount
   */
  def create(pollVote: PollVote, streamerChannelID: String): Future[PollVote] = {
  
    
    // Transaction to add vote and update event confidence amount
    val transactionAction = for {
      // Add the vote
      savedVote <- addVoteAction(pollVote, streamerChannelID)
      
      // Get the event ID for this poll
      eventPollOpt <- eventPollsTable.filter(_.pollId === pollVote.pollId).result.headOption
      
      // Update the event's confidence amount if poll exists
      updateResult <- eventPollOpt match {
        case Some(eventPoll) => 
          // First get the current confidence amount
          streamerEventsTable
            .filter(_.eventId === eventPoll.eventId)
            .map(_.currentConfidenceAmount)
            .result
            .headOption
            .flatMap {
              case Some(currentAmount) =>
                // Then update with the new total
                val newAmount = currentAmount + pollVote.confidenceAmount
                streamerEventsTable
                  .filter(_.eventId === eventPoll.eventId)
                  .map(e => (e.currentConfidenceAmount, e.updatedAt))
                  .update((newAmount, Instant.now()))
              case None => 
                DBIO.successful(0) // No event found
            }
            
        case None => 
          DBIO.successful(0) // No poll found
      }
    } yield savedVote
    
    db.run(transactionAction.transactionally)
  }
  
  /**
   * Get a vote by ID
   */
  def getById(voteId: Int): Future[Option[PollVote]] = db.run {
    pollVotesTable.filter(_.voteId === voteId).result.headOption
  }

  /**
   * Get a vote by ID
   */
  def updateConfidenceAmountAction(voteId: Int, newConfidenceAmount: Int): DBIO[Int] =
    pollVotesTable.filter(_.voteId === voteId).map(_.confidenceAmount).update(newConfidenceAmount)

  
  /**
   * Get all votes for a poll
   */
  def getByPollId(pollId: Int): Future[Seq[PollVote]] = db.run {
    getByPollIdAction(pollId)
  }
  
  def getByPollIdAction(pollId: Int): DBIO[Seq[PollVote]] =
    pollVotesTable.filter(_.pollId === pollId).result  
  
  /**
   * Get all votes for a poll option
   */
  def getByOptionId(optionId: Int): Future[Seq[PollVote]] = db.run {
    pollVotesTable.filter(_.optionId === optionId).result
  }
  
  /**
   * Get all votes by a user for a specific poll
   */
  def getByUserAndPoll(userId: Long, pollId: Int): Future[Seq[PollVote]] = db.run {
    pollVotesTable.filter(v => v.userId === userId && v.pollId === pollId).result
  }
  
  /**
   * Check if a user has already voted in a poll
   */
  def hasUserVotedInPoll(userId: Long, pollId: Int): Future[Boolean] = db.run {
    pollVotesTable
      .filter(v => v.userId === userId && v.pollId === pollId)
      .exists
      .result
  }

  /**
   * Count votes for each option in a poll
   * Returns a map of optionId -> count
   */
  def countVotesByOption(pollId: Int): Future[Map[Int, Int]] = db.run {
    pollVotesTable
      .filter(_.pollId === pollId)
      .groupBy(_.optionId)
      .map { case (optionId, votes) => optionId -> votes.length }
      .result
  }.map(_.toMap)
  
  /**
   * Sum the confidence amounts for each option in a poll
   * Returns a map of optionId -> total confidence
   */
  def sumConfidenceByOption(pollId: Int): DBIO[Map[Int, Int]] =  {
    pollVotesTable
      .filter(_.pollId === pollId)
      .groupBy(_.optionId)
      .map { case (optionId, votes) => optionId -> votes.map(_.confidenceAmount).sum }
      .result
  }.map(_.collect { case (optionId, Some(sum)) => optionId -> sum }.toMap)

  def sumConfidenceByOptionFuture(pollId: Int): Future[Map[Int, Int]] = db.run(sumConfidenceByOption(pollId))

  // Get table query for use by other repositories
  def getTableQuery = pollVotesTable
  
}
