package services

import models.*
import models.repository.*

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserEventDataService @Inject()(
  streamerEventRepository: StreamerEventRepository,
  userStreamerStateRepository: UserStreamerStateRepository,
  pollService: PollService
)(implicit ec: ExecutionContext) {

  case class UserEventData(
    frontalEventsComplete: Seq[FrontalStreamerEvent],
    channelBalanceMap: Map[String, Int],
    extraActiveEventsWithFrontal: Seq[FrontalStreamerEvent]
  )

  def getUserEventData(userId: Long): Future[UserEventData] = {
    for {
      userStreamerStates <- userStreamerStateRepository.getByUserId(userId)
      channelIds = userStreamerStates.map(_.streamerChannelId)
      channelBalanceMap = userStreamerStates.map(uss => uss.streamerChannelId -> uss.currentBalanceNumber).toMap
      
      userEvents <- getUserEvents(channelIds)
      extraEvents <- getExtraActiveEvents(userEvents)
    } yield UserEventData(userEvents, channelBalanceMap, extraEvents)
  }

  private def getUserEvents(channelIds: Seq[String]): Future[Seq[FrontalStreamerEvent]] = {
    for {
      events <- Future.sequence(channelIds.map(streamerEventRepository.getActiveEventsByChannel))
      flatEvents = events.flatten
      frontalEvents = flatEvents.flatMap(FrontalStreamerEvent.apply)
      frontalEventsComplete <- Future.sequence(frontalEvents.map(pollService.completeFrontalPoll))
    } yield frontalEventsComplete
  }

  private def getExtraActiveEvents(userEvents: Seq[FrontalStreamerEvent]): Future[Seq[FrontalStreamerEvent]] = {
    val userEventIds = userEvents.map(_.eventId).toSet
    
    for {
      allActiveEvents <- streamerEventRepository.getAllActiveEvents()
      extraActiveEvents = allActiveEvents.filter(event =>
        event.eventId.isDefined && !userEventIds.contains(event.eventId.get))
      extraActiveEventsWithFrontal = extraActiveEvents.flatMap(FrontalStreamerEvent.apply)
    } yield extraActiveEventsWithFrontal
  }

  def getUserChannelBalance(userId: Long, channelId: String): Future[Int] = {
    userStreamerStateRepository.getUserStreamerBalance(userId, channelId).map(_.getOrElse(0))
  }

  def joinUserToEvent(userId: Long, eventId: Int): Future[Either[String, String]] = {
    streamerEventRepository.getById(eventId).flatMap {
      case Some(event) =>
        userStreamerStateRepository.getUserStreamerBalance(userId, event.channelId).flatMap {
          case None =>
            userStreamerStateRepository.create(userId, event.channelId).map { _ =>
              Right(s"Te uniste al evento: ${event.eventName}")
            }
          case Some(_) =>
            Future.successful(Right("Ya eras parte del evento"))
        }
      case None =>
        Future.successful(Left("Event not found"))
    }
  }
}
