package controllers

import java.net.URI
import java.time.Instant
import javax.inject._

import models._
import models.repository._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.{Logging, LoggingAdapter}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Source}
import play.api.Logger
import play.api.mvc._
import play.api.i18n.I18nSupport
import play.api.data._
import play.api.data.Forms._
import forms.Forms.*
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
                                ytStreamerRepository: YtStreamerRepository)
                               (implicit actorSystem: ActorSystem,
                                mat: Materializer,
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
    // Get user's streamers and all events
    for {
      streamers <- ytStreamerRepository.getAll()
      events <- streamerEventRepository.list()
    } yield {
      Ok(views.html.rivalTeamsEventForm(
        eventWithPollForm,
        events,
        streamers,
        request.identity
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
          currentConfidenceAmount = 1000,
          startTime = startTime
        )

        // Process the event and poll creation
        (for {
          // Create the event
          createdEvent <- streamerEventRepository.create(event)

          // Create the poll
          poll = EventPoll(
            eventId = createdEvent.eventId.get,
            pollQuestion = formData.poll.pollQuestion
          )
          createdPoll <- eventPollRepository.create(poll)

          // Create the poll options
          _ <- pollOptionRepository.createMultiple(createdPoll.pollId.get, formData.poll.options)

          // Get updated list of streamers and events for the view
          events <- streamerEventRepository.list()
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
    streamerEventRepository.endEvent(eventId).map { result =>
      if (result > 0) {
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
    streamerEventRepository.closeEvent(eventId).map { result =>
      if (result > 0) {
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
          _ <- streamerEventRepository.closeEvent(eventId)
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


}
