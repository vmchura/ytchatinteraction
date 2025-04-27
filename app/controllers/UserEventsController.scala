package controllers

import javax.inject._
import models._
import models.repository._
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.PollService

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
  pollService: PollService
)(implicit ec: ExecutionContext) 
  extends SilhouetteController(scc) with I18nSupport {

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
    
    for {
      // Get the channel IDs the user has a relationship with
      userStreamerStates <- userStreamerStateRepository.getByUserId(userId)
      channelIds = userStreamerStates.map(_.streamerChannelId)
      
      // Get active events for these channels
      events <- Future.sequence(channelIds.map(streamerEventRepository.getActiveEventsByChannel))
      flatEvents = events.flatten
      frontalEvents = flatEvents.flatMap(FrontalStreamerEvent.apply)
      frontalEventsComplete <- Future.sequence(frontalEvents.map(pollService.completeFrontalPoll))
      
      // Get user balances for all channel IDs
      userBalances <- Future.sequence(channelIds.map(channelId => 
                         userStreamerStateRepository.getUserStreamerBalance(userId, channelId)))
      
      // Create a map of channel ID to user balance
      channelBalanceMap = channelIds.zip(userBalances.flatten).toMap
      
      // Get all active events the user is not participating in
      allActiveEvents <- streamerEventRepository.getAllActiveEvents()
      userEventIds = flatEvents.flatMap(_.eventId).toSet
      extraActiveEvents = allActiveEvents.filter(event => 
        event.eventId.isDefined && !userEventIds.contains(event.eventId.get))
      extraActiveEventsWithFrontal = extraActiveEvents.flatMap(FrontalStreamerEvent.apply)
      
    } yield {
      Ok(views.html.userEvents(
        frontalEventsComplete,
        channelBalanceMap,
        voteForm,
        request.identity,
        extraActiveEventsWithFrontal
      ))
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
                voteData.confidence
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
}

/**
 * Form data for submitting a vote
 */
case class VoteFormData(optionId: Int, confidence: Int, eventId: Int, pollId: Int)
