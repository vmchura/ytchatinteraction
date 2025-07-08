package controllers

import javax.inject._
import models._
import models.repository._
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.api.Logger
import services.{PollService, EventUpdateService, TournamentService}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import java.net.URI

import scala.concurrent.{ExecutionContext, Future}

/**
 * Controller for managing user's interactions with events
 */
@Singleton
class UserEventsController @Inject()(
  val scc: SilhouetteControllerComponents,
  streamerEventRepository: StreamerEventRepository,
  eventPollRepository: EventPollRepository,
  pollOptionRepository: PollOptionRepository,
  userStreamerStateRepository: UserStreamerStateRepository,
  pollService: PollService,
  eventUpdateService: EventUpdateService,
  tournamentService: TournamentService
)(implicit ec: ExecutionContext, 
  system: ActorSystem,
  mat: Materializer) 
  extends SilhouetteController(scc) with I18nSupport {
  
  private val logger = Logger(getClass)

  // Form for voting on a poll option
  val voteForm = Form(
    mapping(
      "optionId" -> number,
      "confidence" -> number(min = 1),
      "eventId" -> number,
      "pollId" -> number
    )(VoteFormData.apply)(nn => Some(nn.optionId, nn.confidence, nn.eventId, nn.pollId))
  )

  /**
   * Display all active events the user has a relationship with and also other available events
   */
  def userEvents = SecuredAction.async { implicit request =>
    val userId = request.identity.userId
    
    // Create WebSocket URL for event updates
    val webSocketUrl = routes.UserEventsController.eventsUpdates.webSocketURL()
    
    for {
      // Get the channel IDs the user has a relationship with
      userStreamerStates <- userStreamerStateRepository.getByUserId(userId)
      channelIds = userStreamerStates.map(_.streamerChannelId)
      channelBalanceMap = userStreamerStates.map(uss => uss.streamerChannelId -> uss.currentBalanceNumber).toMap
      // Get active events for these channels
      events <- Future.sequence(channelIds.map(streamerEventRepository.getActiveEventsByChannel))
      flatEvents = events.flatten
      frontalEvents = flatEvents.flatMap(FrontalStreamerEvent.apply)
      frontalEventsComplete <- Future.sequence(frontalEvents.map(pollService.completeFrontalPoll))

      
      // Get all active events the user is not participating in
      allActiveEvents <- streamerEventRepository.getAllActiveEvents()
      userEventIds = flatEvents.flatMap(_.eventId).toSet
      extraActiveEvents = allActiveEvents.filter(event => 
        event.eventId.isDefined && !userEventIds.contains(event.eventId.get))
      extraActiveEventsWithFrontal = extraActiveEvents.flatMap(FrontalStreamerEvent.apply)
      
      // Get open tournaments
      openTournaments <- tournamentService.getOpenTournaments
      
      // Check which tournaments the user is already registered for
      userRegistrations <- Future.sequence(openTournaments.map { tournament =>
        tournamentService.isUserRegistered(tournament.id, userId).map(tournament -> _)
      })
      tournamentsWithRegistrationStatus = userRegistrations.toMap
      
    } yield {
      Ok(views.html.userEvents(
        frontalEventsComplete,
        channelBalanceMap,
        voteForm,
        request.identity,
        extraActiveEventsWithFrontal,
        webSocketUrl,
        openTournaments,
        tournamentsWithRegistrationStatus
      ))
    }
  }

  /**
   * WebSocket endpoint for event updates
   */
  def eventsUpdates: WebSocket = WebSocket.acceptOrResult[String, String] { request =>
    Future.successful {
      if (sameOriginCheck(request)) {
        Right(eventUpdateService.eventsFlow())
      } else {
        Left(Forbidden("Forbidden"))
      }
    }
  }
  
  /**
   * Checks that the WebSocket comes from the same origin.
   */
  private def sameOriginCheck(implicit rh: RequestHeader): Boolean = {
    rh.headers.get("Origin") match {
      case Some(originValue) if originMatches(originValue) =>
        logger.debug(s"originCheck: originValue = $originValue")
        true
      case Some(badOrigin) =>
        logger.error(s"originCheck: rejecting request because Origin header value $badOrigin is not in the same origin")
        false
      case None =>
        logger.error("originCheck: rejecting request because no Origin header found")
        false
    }
  }
  
  /**
   * Returns true if the value of the Origin header contains an acceptable value.
   */
  private def originMatches(origin: String): Boolean = {
    try {
      val url = new URI(origin)
      (url.getHost == "localhost" || url.getHost == "evolutioncomplete.com" || url.getHost == "91.99.13.219") &&
        (url.getPort match { case 9000 | 5000 | 19001 => true; case _ => false })
    } catch {
      case e: Exception => false
    }
  }
  
  /**
   * Process a vote on a poll option
   */
  def submitVote = SecuredAction.async { implicit request =>
    voteForm.bindFromRequest().fold(
      formWithErrors => {
        // Redirect back to the events page with an error
        Future.successful(Redirect(routes.UserEventsController.userEvents())
          .flashing("error" -> "Invalid form submission"))
      },
      voteData => {
        val userId = request.identity.userId
        
        // Check if the user has enough balance
        (for {
          event <- streamerEventRepository.getById(voteData.eventId)
          
          result <- event match {
            case Some(evt) if evt.endTime.isEmpty =>
              // Register the vote
              pollService.registerPollVote(
                voteData.pollId,
                voteData.optionId,
                userId,
                None, // No message for this type of vote
                voteData.confidence,
                evt.channelId
              ).map(_ => 
                Redirect(routes.UserEventsController.userEvents())
                  .flashing("success" -> "Vote registered successfully")
              )
              
            case Some(_) =>
              // Event is closed
              Future.successful(
                Redirect(routes.UserEventsController.userEvents())
                  .flashing("error" -> "This event is no longer accepting votes")
              )
              
            case None =>
              // Event not found
              Future.successful(
                Redirect(routes.UserEventsController.userEvents())
                  .flashing("error" -> "Event not found")
              )
          }
        } yield result).recover {
          case e: Exception =>
            Redirect(routes.UserEventsController.userEvents())
              .flashing("error" -> s"Error registering vote: ${e.getMessage}")
        }
      }
    )
  }
  
  /**
   * Join a new event
   */
  def joinEvent(eventId: Int) = SecuredAction.async { implicit request =>
    val userId = request.identity.userId
    
    (for {
      // Get the event
      eventOpt <- streamerEventRepository.getById(eventId)
      
      result <- eventOpt match {
        case Some(event)  =>
          // Check if user already has a relationship with this channel
          userStreamerStateRepository.getUserStreamerBalance(userId, event.channelId).flatMap { 
            existingBalance =>
              // If user doesn't have a relationship, create one with 0 balance
              if (existingBalance.isEmpty) {
                userStreamerStateRepository.create(userId, event.channelId, 0).map { _ =>
                  Redirect(routes.UserEventsController.userEvents())
                    .flashing("success" -> s"Te uniste al evento: ${event.eventName}")
                }
              } else {
                // User already has a relationship
                Future.successful(
                  Redirect(routes.UserEventsController.userEvents())
                    .flashing("info" -> "Ya eras parte del evento")
                )
              }
          }

          
        case None =>
          // Event not found
          Future.successful(
            Redirect(routes.UserEventsController.userEvents())
              .flashing("error" -> "Event not found")
          )
      }
    } yield result).recover {
      case e: Exception =>
        Redirect(routes.UserEventsController.userEvents())
          .flashing("error" -> s"Error joining event: ${e.getMessage}")
    }
  }
  
  /**
   * Register user for a tournament
   */
  def registerForTournament(tournamentId: Long) = SecuredAction.async { implicit request =>
    val userId = request.identity.userId
    
    tournamentService.registerUser(tournamentId, userId).map {
      case Right(_) =>
        Redirect(routes.UserEventsController.userEvents())
          .flashing("success" -> "Successfully registered for tournament!")
      case Left(error) =>
        Redirect(routes.UserEventsController.userEvents())
          .flashing("error" -> error)
    }.recover {
      case e: Exception =>
        logger.error(s"Error registering user $userId for tournament $tournamentId", e)
        Redirect(routes.UserEventsController.userEvents())
          .flashing("error" -> s"Error registering for tournament: ${e.getMessage}")
    }
  }
}

/**
 * Form data for submitting a vote
 */
case class VoteFormData(optionId: Int, confidence: Int, eventId: Int, pollId: Int)
