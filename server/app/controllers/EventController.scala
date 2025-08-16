package controllers

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.*
import models.*
import models.repository.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.mvc.*
import forms.Forms.*
import play.api.libs.json._
import services.{ActiveLiveStream, ChatService, PollService, EventUpdateService}
import utils.auth.WithAdmin
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EventController @Inject()(components: DefaultSilhouetteControllerComponents,
                                inputSanitizer: InputSanitizer,
                                streamerEventRepository: StreamerEventRepository,
                                eventPollRepository: EventPollRepository,
                                pollOptionRepository: PollOptionRepository,
                                ytStreamerRepository: YtStreamerRepository,
                                userStreamerStateRepository: UserStreamerStateRepository,
                                pollService: PollService,
                                actorSystem: ActorSystem,
                                activeLiveStream: ActiveLiveStream,
                                chatService: ChatService,
                                eventUpdateService: EventUpdateService,
                                pollVoteRepository: PollVoteRepository)
                               (implicit mat: Materializer,
                                executionContext: ExecutionContext,
                                webJarsUtil: org.webjars.play.WebJarsUtil)
  extends SilhouetteController(components) with RequestMarkerContext {

  private def parseLocalDateTimeToInstant(dateTimeString: String): Instant = {
    val isoFormatString = dateTimeString + ":00Z"
    Instant.parse(isoFormatString)
  }

  def eventManagement: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    val webSocketUrl = routes.HomeController.streamerevents().webSocketURL()
    for {
      streamers <- ytStreamerRepository.getAll()
      events <- streamerEventRepository.list()
      validEvents = events.filter(_.endTime.isEmpty)
      activeEventIds = validEvents.flatMap(_.eventId)
      allPolls <- Future.sequence(activeEventIds.map(eventPollRepository.getByEventId))
      polls = allPolls.flatten
      allPollOptions <- Future.sequence(polls.flatMap(_.pollId).map(pollOptionRepository.getByPollId))
      pollOptionsMap = polls.flatMap(_.pollId).zip(allPollOptions).toMap
      eventPollMap = activeEventIds.zip(polls).toMap

    } yield {
      Ok(views.html.rivalTeamsEventForm(
        eventWithPollForm,
        validEvents,
        streamers,
        request.identity,
        eventPollMap,
        pollOptionsMap,
        activeLiveStream.list(),
        webSocketUrl
      ))
    }
  }

  def fullEventForm: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    for {
      streamers <- ytStreamerRepository.getAll()
      events <- streamerEventRepository.list()
    } yield {
      Ok(views.html.eventManagement(
        eventWithPollForm,
        events,
        streamers,
        request.identity
      ))
    }
  }

  def createEvent: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    forms.Forms.eventWithPollForm.bindFromRequest().fold(
      formWithErrors => {
        for {
          streamers <- ytStreamerRepository.getAll()
          events <- streamerEventRepository.list()
        } yield {
          BadRequest(views.html.eventManagement(
            formWithErrors,
            events,
            streamers,
            request.identity
          ))
        }
      },
      formData => {
        val startTime = parseLocalDateTimeToInstant(formData.event.startTime)

        val event = StreamerEvent(
          channelId = formData.event.channelId,
          eventName = formData.event.eventName,
          eventDescription = formData.event.eventDescription,
          eventType = formData.event.eventType,
          currentConfidenceAmount = formData.event.currentConfidenceAmount,
          startTime = startTime
        )

        (for {
          createdEvent <- pollService.createEvent(event, formData.poll)
          _ = eventUpdateService.broadcastNewEvent(createdEvent)
        } yield {
          Redirect(routes.EventController.eventManagement())
            .flashing("success" -> "Event created successfully")
        }).recover {
          case e: Exception =>
            logger.error("Error creating event", e)
            Redirect(routes.EventController.eventManagement())
              .flashing("error" -> s"Error creating event: ${e.getMessage}")
        }
      }
    )
  }

  def endEvent(eventId: Int): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    pollService.endEvent(eventId).map { result =>
      if (result.forall(_ == true)) {
        Redirect(routes.EventController.eventManagement())
          .flashing("success" -> "Stopped accepting new votes for this event")
      } else {
        Redirect(routes.EventController.eventManagement())
          .flashing("error" -> "Event not found or already inactive")
      }
    }.recover {
      case e: Exception =>
        logger.error("Error stopping votes for event", e)
        Redirect(routes.EventController.eventManagement())
          .flashing("error" -> s"Error stopping votes: ${e.getMessage}")
    }
  }

  def closeEvent(eventId: Int): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    pollService.closeEvent(eventId).map { result =>
      if (result.forall(_ == true)) {
        Redirect(routes.EventController.eventManagement())
          .flashing("success" -> "Event closed successfully")
      } else {
        Redirect(routes.EventController.eventManagement())
          .flashing("error" -> "Event not found")
      }
    }.recover {
      case e: Exception =>
        logger.error("Error closing event", e)
        Redirect(routes.EventController.eventManagement())
          .flashing("error" -> s"Error closing event: ${e.getMessage}")
    }
  }

  def eventHistory: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    for {
      events <- streamerEventRepository.list()
    } yield {
      Ok(views.html.eventHistory(events, request.identity))
    }
  }

  def selectWinnerForm(eventId: Int): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    for {
      eventOpt <- streamerEventRepository.getById(eventId)
      polls <- eventPollRepository.getByEventId(eventId)
      pollId = polls.headOption.flatMap(_.pollId)
      options <- pollId match {
        case Some(id) => pollOptionRepository.getByPollId(id)
        case None => Future.successful(Seq.empty)
      }
    } yield {
      eventOpt match {
        case Some(event) if polls.nonEmpty =>
          Ok(views.html.selectWinner(
            forms.Forms.setWinnerForm,
            event,
            polls.head,
            options,
            request.identity
          ))
        case Some(_) =>
          Redirect(routes.EventController.eventManagement())
            .flashing("error" -> "No poll found for this event")
        case None =>
          Redirect(routes.EventController.eventManagement())
            .flashing("error" -> "Event not found")
      }
    }
  }

  def setWinnerAndClose(eventId: Int): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    forms.Forms.setWinnerForm.bindFromRequest().fold(
      formWithErrors => {
        for {
          eventOpt <- streamerEventRepository.getById(eventId)
          polls <- eventPollRepository.getByEventId(eventId)
          pollId = polls.headOption.flatMap(_.pollId)
          options <- pollId match {
            case Some(id) => pollOptionRepository.getByPollId(id)
            case None => Future.successful(Seq.empty)
          }
        } yield {
          eventOpt match {
            case Some(event) if polls.nonEmpty =>
              BadRequest(views.html.selectWinner(
                formWithErrors,
                event,
                polls.head,
                options,
                request.identity
              ))
            case _ =>
              Redirect(routes.EventController.eventManagement())
                .flashing("error" -> "Event or poll not found")
          }
        }
      },
      formData => {
        (for {
          polls <- eventPollRepository.getByEventId(eventId)
          pollId = polls.headOption.flatMap(_.pollId).getOrElse(
            throw new IllegalStateException("No poll found for this event")
          )

          _ <- eventPollRepository.setWinnerOption(pollId, formData.optionId)
          _ <- pollService.closeEvent(eventId)

          closedEvent <- streamerEventRepository.getById(eventId)
          _ = closedEvent.foreach(eventUpdateService.broadcastEventClosed)

          spread <- pollService.spreadPollConfidence(eventId, pollId)
        } yield {
          Redirect(routes.EventController.eventManagement())
            .flashing("success" -> "Event closed successfully and winner selected")
        }).recover {
          case e: Exception =>
            logger.error("Error setting winner and closing event", e)
            Redirect(routes.EventController.eventManagement())
              .flashing("error" -> s"Error closing event: ${e.getMessage}")
        }
      }
    )
  }

  def recentPolls: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    val cutoffTime = Instant.now().minus(24, ChronoUnit.HOURS)

    val eventsQuery = for {
      allEvents <- streamerEventRepository.list()

      recentEvents = allEvents.filter(event =>
        event.createdAt.isDefined && event.createdAt.get.isAfter(cutoffTime)
      )

      eventPollsWithOptions <- Future.sequence(
        recentEvents.flatMap(_.eventId).map { eventId =>
          for {
            polls <- eventPollRepository.getByEventId(eventId)

            pollsWithOptions <- Future.sequence(
              polls.flatMap(_.pollId).map { pollId =>
                for {
                  options <- pollOptionRepository.getByPollId(pollId)
                  votes <- pollVoteRepository.getByPollId(pollId)

                  votesByOption = votes.groupBy(_.optionId)
                  confidenceSumByOption = votesByOption.map {
                    case (optionId, votes) =>
                      optionId -> votes.map(_.confidenceAmount).sum
                  }

                  optionsWithConfidence = options.map { option =>
                    option.optionId.map { optionId =>
                      PollOptionData(
                        optionText = option.optionText,
                        optionProbability = option.confidenceRatio,
                        totalConfidence = confidenceSumByOption.getOrElse(optionId, 0)
                      )
                    }.getOrElse(
                      PollOptionData(
                        optionText = option.optionText,
                        optionProbability = option.confidenceRatio,
                        totalConfidence = 0
                      )
                    )
                  }
                } yield (pollId, polls, optionsWithConfidence)
              }
            )

            eventData = polls.flatMap { poll =>
              pollsWithOptions.find(_._1 == poll.pollId.getOrElse(-1)).map {
                case (_, pollList, options) =>
                  val matchingEvent = recentEvents.find(_.eventId.contains(eventId)).get
                  PollData(
                    title = matchingEvent.eventName,
                    isActive = matchingEvent.isActive,
                    hasNoEndTime = matchingEvent.endTime.isEmpty,
                    options = options
                  )
              }
            }
          } yield eventData
        }
      )
    } yield eventPollsWithOptions.flatten

    eventsQuery.map { pollDataList =>
      Ok(Json.toJson(pollDataList))
    }.recover {
      case e: Exception =>
        logger.error("Error retrieving recent polls", e)
        InternalServerError(Json.obj("error" -> s"Error retrieving polls: ${e.getMessage}"))
    }
  }
}
