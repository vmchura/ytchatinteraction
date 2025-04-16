package models.repository

import models.PollVote
import models.component._
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
  with StreamerEventComponent with UserComponent with YtStreamerComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._
  
  /**
   * Create a new vote for a poll option
   */
  def create(pollVote: PollVote): Future[PollVote] = {
    val voteWithTimestamp = pollVote.copy(
      createdAt = Some(Instant.now())
    )
    
    db.run {
      (pollVotesTable returning pollVotesTable.map(_.voteId)
        into ((vote, id) => vote.copy(voteId = Some(id)))
      ) += voteWithTimestamp
    }
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
   * Delete a vote
   */
  def delete(voteId: Int): Future[Int] = db.run {
    pollVotesTable.filter(_.voteId === voteId).delete
  }
  
  /**
   * Delete all votes for a poll
   */
  def deleteByPollId(pollId: Int): Future[Int] = db.run {
    pollVotesTable.filter(_.pollId === pollId).delete
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
  def sumConfidenceByOption(pollId: Int): Future[Map[Int, Long]] = db.run {
    pollVotesTable
      .filter(_.pollId === pollId)
      .groupBy(_.optionId)
      .map { case (optionId, votes) => optionId -> votes.map(_.confidenceAmount).sum }
      .result
  }.map(_.collect { case (optionId, Some(sum)) => optionId -> sum }.toMap)
  
  // Get table query for use by other repositories
  def getTableQuery = pollVotesTable
}
