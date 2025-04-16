package models

import play.api.libs.json._
import java.time.Instant

case class PollVote(
  voteId: Option[Int] = None,
  pollId: Int,
  optionId: Int,
  userId: Long,
  createdAt: Option[Instant] = None,
  confidenceAmount: Long = 0,
  messageByChatOpt: Option[String] = None
)

object PollVote {
  implicit val pollVoteFormat: OFormat[PollVote] = Json.format[PollVote]
}
