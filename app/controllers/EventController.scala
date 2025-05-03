package controllers

import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.*
import models.*
import models.repository.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.{Logging, LoggingAdapter}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Source}
import play.api.Logger
import play.api.mvc.*
import play.api.i18n.I18nSupport
import play.api.data.*
import play.api.data.Forms.*
import forms.Forms.*
import play.api.libs.json._
import services.{ActiveLiveStream, ChatService, PollService, EventUpdateService}

import scala.concurrent.{ExecutionContext, Future}

/**
 * A very simple chat client using websockets.
 */
@Singleton
class EventController @Inject()(val scc: SilhouetteControllerComponents,
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
  extends SilhouetteController(scc) with RequestMarkerContext {

  private def parseLocalDateTimeToInstant(dateTimeString: String): Instant = {
    // Append ":00Z" to convert from "YYYY-MM-DDThh:mm" to "YYYY-MM-DDThh:mm:00Z"
    val isoFormatString = dateTimeString + ":00Z"
    Instant.parse(isoFormatString)
  }

  // Event management page (now shows the rival teams form by default)
  def eventManagement: Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    // Get user's streamers, all events, and poll options for active events
    val webSocketUrl = routes.HomeController.streamerevents().webSocketURL()
    for {
      streamers <- ytStreamerRepository.getAll()
      events <- streamerEventRepository.list()
      validEvents = events.filter(_.endTime.isEmpty)
      // Get all active event IDs (events that don't have an end time)
      activeEventIds = validEvents.flatMap(_.eventId)
      // Get polls for all active events
      allPolls <- Future.sequence(activeEventIds.map(eventPollRepository.getByEventId))
      // Flatten the sequence of polls
      polls = allPolls.flatten
      // Get poll options for all polls
      allPollOptions <- Future.sequence(polls.flatMap(_.pollId).map(pollOptionRepository.getByPollId))
      // Create a map of poll ID to options
      pollOptionsMap = polls.flatMap(_.pollId).zip(allPollOptions).toMap
      // Create a map of event ID to poll
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
  
  // Full event creation form
  def fullEventForm: Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    // Get user's streamers and all events
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

  // Create a new event with a poll
  def createEvent: Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
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
        // Parse the start time
        val startTime = parseLocalDateTimeToInstant(formData.event.startTime)

        // Create the event
        val event = StreamerEvent(
          channelId = formData.event.channelId,
          eventName = formData.event.eventName,
          eventDescription = formData.event.eventDescription,
          eventType = formData.event.eventType,
          currentConfidenceAmount = formData.event.currentConfidenceAmount,
          startTime = startTime
        )

        // Process the event and poll creation
        (for {
          // Create the event
          createdEvent <- pollService.createEvent(event, formData.poll)
          // Broadcast the event creation to connected clients
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

  // Stop accepting new votes for an event
  def endEvent(eventId: Int): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
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
  
  // Close an event without setting a winner
  def closeEvent(eventId: Int): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
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

  // Get all event history (including ended events)
  def eventHistory: Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    for {
      // Get all events including those that are not active
      events <- streamerEventRepository.list()
    } yield {
      Ok(views.html.eventHistory(events, request.identity))
    }
  }

  // Show form to select winner and close an event
  def selectWinnerForm(eventId: Int): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
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

  // Process winner selection and close the event
  def setWinnerAndClose(eventId: Int): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
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
          // Get the poll for this event
          polls <- eventPollRepository.getByEventId(eventId)
          pollId = polls.headOption.flatMap(_.pollId).getOrElse(
            throw new IllegalStateException("No poll found for this event")
          )

          // Set the winner option
          _ <- eventPollRepository.setWinnerOption(pollId, formData.optionId)
          // Close the event
          _ <- pollService.closeEvent(eventId)
          
          // Get the updated event to broadcast
          closedEvent <- streamerEventRepository.getById(eventId)
          _ = closedEvent.foreach(eventUpdateService.broadcastEventClosed)
          
          // Calculate confidence spread
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

  /**
   * API endpoint that returns all current polls and polls from the past 24 hours
   * in a JSON format with only essential data.
   */
  def recentPolls: Action[AnyContent] = Action.async { implicit request =>
    // Define the cutoff time (24 hours ago)
    val cutoffTime = Instant.now().minus(24, ChronoUnit.HOURS)
    
    // Get all active events and events in the last 24 hours
    val eventsQuery = for {
      // Get all events
      allEvents <- streamerEventRepository.list()
      
      // Filter to get only active events or those created in last 24 hours
      recentEvents = allEvents.filter(event => 
        (event.createdAt.isDefined && event.createdAt.get.isAfter(cutoffTime))
      )
      
      // For each event, get its poll and options
      eventPollsWithOptions <- Future.sequence(
        recentEvents.flatMap(_.eventId).map { eventId =>
          for {
            // Get polls for this event
            polls <- eventPollRepository.getByEventId(eventId)
            
            // For each poll, get its options and votes
            pollsWithOptions <- Future.sequence(
              polls.flatMap(_.pollId).map { pollId =>
                for {
                  options <- pollOptionRepository.getByPollId(pollId)
                  votes <- pollVoteRepository.getByPollId(pollId)
                  
                  // Group votes by option and sum confidence
                  votesByOption = votes.groupBy(_.optionId)
                  confidenceSumByOption = votesByOption.map { 
                    case (optionId, votes) => 
                      optionId -> votes.map(_.confidenceAmount).sum 
                  }
                  
                  // Combine options with their total confidence
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
            
            // Map event to a list of poll data objects
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
    
    // Return as JSON
    eventsQuery.map { pollDataList =>
      Ok(Json.toJson(pollDataList))
    }.recover {
      case e: Exception =>
        logger.error("Error retrieving recent polls", e)
        InternalServerError(Json.obj("error" -> s"Error retrieving polls: ${e.getMessage}"))
    }
  }
}
