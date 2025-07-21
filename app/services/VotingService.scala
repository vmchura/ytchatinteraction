package services

import forms.{Forms, VoteFormData}
import models.*
import models.repository.StreamerEventRepository
import play.api.Logger
import play.api.mvc.{AnyContent, Request}
import play.api.data.FormBinding.Implicits.formBinding

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VotingService @Inject()(
  streamerEventRepository: StreamerEventRepository,
  pollService: PollService
)(implicit ec: ExecutionContext) {

  private val logger = Logger(getClass)

  def parseVoteRequest(implicit request: Request[AnyContent]): Option[VoteFormData] = {
    Forms.voteForm.bindFromRequest().fold(
      formWithErrors => {
        logger.warn(s"Form binding failed: ${formWithErrors.errors}")
        None
      },
      validVoteData => Some(validVoteData)
    )
  }

  def processVote(voteData: VoteFormData, userId: Long): Future[VoteResult] = {
    streamerEventRepository.getById(voteData.eventId).flatMap {
      case Some(event) if event.endTime.isEmpty =>
        pollService.registerPollVote(
          voteData.pollId,
          voteData.optionId,
          userId,
          None,
          voteData.confidence,
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

  def validateVoteRequest(voteData: VoteFormData, userId: Long, userBalance: Int): Future[Either[String, Unit]] = {
    if (voteData.confidence > userBalance) {
      Future.successful(Left("Insufficient balance for this vote"))
    } else if (voteData.confidence <= 0) {
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
