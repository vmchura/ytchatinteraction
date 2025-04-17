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
  
  /**
   * Create a new vote for a poll option and update the event's confidence amount
   */
  def create(pollVote: PollVote): Future[PollVote] = {
    val voteWithTimestamp = pollVote.copy(
      createdAt = Some(Instant.now())
    )
    
    val addVoteAction = (pollVotesTable returning pollVotesTable.map(_.voteId)
      into ((vote, id) => vote.copy(voteId = Some(id)))
    ) += voteWithTimestamp
    
    // Transaction to add vote and update event confidence amount
    val transactionAction = for {
      // Add the vote
      savedVote <- addVoteAction
      
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
   * Get all votes for a poll
   */
  def getByPollId(pollId: Int): Future[Seq[PollVote]] = db.run {
    pollVotesTable.filter(_.pollId === pollId).result
  }
  
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
   * Delete a vote and update the event's confidence amount
   */
  def delete(voteId: Int): Future[Int] = {
    // Transaction to delete vote and update event confidence amount
    val transactionAction = for {
      // Get the vote to be deleted
      voteOpt <- pollVotesTable.filter(_.voteId === voteId).result.headOption
      
      // Process if vote exists
      result <- voteOpt match {
        case Some(vote) =>
          for {
            // Get the associated event poll
            eventPollOpt <- eventPollsTable.filter(_.pollId === vote.pollId).result.headOption
            
            // Delete the vote
            deleteResult <- pollVotesTable.filter(_.voteId === voteId).delete
            
            // Update the event's confidence if poll and event exist
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
                      // Then update with the reduced total
                      val newAmount = math.max(0, currentAmount - vote.confidenceAmount) // Ensure non-negative
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
          } yield deleteResult
          
        case None =>
          DBIO.successful(0) // Vote not found
      }
    } yield result
    
    db.run(transactionAction.transactionally)
  }
  
  /**
   * Delete all votes for a poll and update the event's confidence amount
   */
  def deleteByPollId(pollId: Int): Future[Int] = {
    // Transaction to delete votes and update event confidence amount
    val transactionAction = for {
      // Get the sum of confidence amounts to subtract
      confidenceSum <- pollVotesTable
        .filter(_.pollId === pollId)
        .map(_.confidenceAmount)
        .sum
        .result
      
      // Get the associated event poll
      eventPollOpt <- eventPollsTable.filter(_.pollId === pollId).result.headOption
      
      // Delete all votes for the poll
      deleteResult <- pollVotesTable.filter(_.pollId === pollId).delete
      
      // Update the event's confidence if poll and event exist and there were votes
      updateResult <- (eventPollOpt, confidenceSum) match {
        case (Some(eventPoll), Some(sum)) if sum > 0 =>
          // First get the current confidence amount
          streamerEventsTable
            .filter(_.eventId === eventPoll.eventId)
            .map(_.currentConfidenceAmount)
            .result
            .headOption
            .flatMap {
              case Some(currentAmount) =>
                // Then update with the reduced total
                val newAmount = math.max(0, currentAmount - sum) // Ensure non-negative
                streamerEventsTable
                  .filter(_.eventId === eventPoll.eventId)
                  .map(e => (e.currentConfidenceAmount, e.updatedAt))
                  .update((newAmount, Instant.now()))
              case None =>
                DBIO.successful(0) // No event found
            }
        case _ =>
          DBIO.successful(0) // No poll found or no confidence to subtract
      }
    } yield deleteResult
    
    db.run(transactionAction.transactionally)
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
  def sumConfidenceByOption(pollId: Int): Future[Map[Int, Int]] = db.run {
    pollVotesTable
      .filter(_.pollId === pollId)
      .groupBy(_.optionId)
      .map { case (optionId, votes) => optionId -> votes.map(_.confidenceAmount).sum }
      .result
  }.map(_.collect { case (optionId, Some(sum)) => optionId -> sum }.toMap)

  // Get table query for use by other repositories
  def getTableQuery = pollVotesTable
}
