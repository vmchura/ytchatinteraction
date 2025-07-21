package services

import models.*
import models.repository.StreamerEventRepository
import play.api.Logger
import play.api.mvc.{AnyContent, Request}
import utils.FormUtils

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VotingService @Inject()(
  streamerEventRepository: StreamerEventRepository,
  pollService: PollService
)(implicit ec: ExecutionContext) {

  private val logger = Logger(getClass)

  case class VoteRequest(
    optionId: Int,
    confidence: Int,
    eventId: Int,
    pollId: Int
  )

  def parseVoteRequest(implicit request: Request[AnyContent]): Option[VoteRequest] = {
    val formData = FormUtils.getFormData

    for {
      optionId <- FormUtils.getFormValueAsInt(formData, "optionId")
      confidence <- FormUtils.getFormValueAsInt(formData, "confidence")
      eventId <- FormUtils.getFormValueAsInt(formData, "eventId")
      pollId <- FormUtils.getFormValueAsInt(formData, "pollId")
    } yield VoteRequest(optionId, confidence, eventId, pollId)
  }

  def processVote(voteRequest: VoteRequest, userId: Long): Future[VoteResult] = {
    streamerEventRepository.getById(voteRequest.eventId).flatMap {
      case Some(event) if event.endTime.isEmpty =>
        pollService.registerPollVote(
          voteRequest.pollId,
          voteRequest.optionId,
          userId,
          None,
          voteRequest.confidence,
          event.channelId
        ).map(_ => VoteResult.Success("Vote registered successfully"))

      case Some(_) =>
        Future.successful(VoteResult.Failure("This event is no longer accepting votes"))

      case None =>
        Future.successful(VoteResult.Failure("Event not found"))
    }.recover {
      case e: Exception =>
        logger.error(s"Error registering vote for user $userId", e)
        VoteResult.Failure(s"Error registering vote: ${e.getMessage}")
    }
  }

  def validateVoteRequest(voteRequest: VoteRequest, userId: Long, userBalance: Int): Future[Either[String, Unit]] = {
    if (voteRequest.confidence > userBalance) {
      Future.successful(Left("Insufficient balance for this vote"))
    } else if (voteRequest.confidence <= 0) {
      Future.successful(Left("Confidence must be positive"))
    } else {
      Future.successful(Right(()))
    }
  }

  sealed trait VoteResult {
    def message: String
  }

  object VoteResult {
    case class Success(message: String) extends VoteResult
    case class Failure(message: String) extends VoteResult
  }
}
