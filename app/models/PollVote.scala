package models

import play.api.libs.json._
import java.time.Instant

case class PollVote(
  voteId: Option[Int] = None,
  pollId: Int,
  optionId: Int,
  userId: Long,
  messageByChatOpt: Option[String],
  confidenceAmount: Int,
  createdAt: Option[Instant] = None,
)

object PollVote {
  implicit val pollVoteFormat: OFormat[PollVote] = Json.format[PollVote]
}
