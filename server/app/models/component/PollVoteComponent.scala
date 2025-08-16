package models.component

import models.PollVote
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

import java.time.Instant

trait PollVoteComponent {
  self: EventPollComponent with PollOptionComponent with UserComponent =>
  
  protected val profile: JdbcProfile
  import profile.api._

  class PollVotesTable(tag: Tag) extends Table[PollVote](tag, "poll_votes") {
    def voteId = column[Int]("vote_id", O.PrimaryKey, O.AutoInc)
    def pollId = column[Int]("poll_id")
    def optionId = column[Int]("option_id")
    def userId = column[Long]("user_id")
    def createdAt = column[Instant]("created_at", O.Default(Instant.now()))
    def confidenceAmount = column[Int]("confidence_amount")
    def messageByChat = column[Option[String]]("message_by_chat")
    
    def pollFk = foreignKey("fk_poll_votes_poll", pollId, eventPollsTable)(_.pollId)
    def optionFk = foreignKey("fk_poll_votes_option", optionId, pollOptionsTable)(_.optionId)
    def userFk = foreignKey("fk_poll_votes_user", userId, usersTable)(_.userId)
    
    def * = (
      voteId.?,
      pollId,
      optionId,
      userId,
      messageByChat,
      confidenceAmount,
      createdAt.?
    ) <> ((PollVote.apply _).tupled, PollVote.unapply)
  }

  val pollVotesTable = TableQuery[PollVotesTable]
}
