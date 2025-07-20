package controllers

import javax.inject.*
import models.*
import models.repository.*
import models.dao.TournamentChallongeDAO
import play.api.data.*
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import play.api.Logger
import _root_.services.{EventUpdateService, PollService, TournamentChallongeService, TournamentService}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import utils.auth.WithAdmin

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserEventsController @Inject()(
                                      components: DefaultSilhouetteControllerComponents,
                                      streamerEventRepository: StreamerEventRepository,
                                      eventPollRepository: EventPollRepository,
                                      pollOptionRepository: PollOptionRepository,
                                      userStreamerStateRepository: UserStreamerStateRepository,
                                      pollService: PollService,
                                      eventUpdateService: EventUpdateService,
                                      tournamentService: TournamentService,
                                      tournamentChallongeService: TournamentChallongeService,
                                      tournamentChallongeDAO: TournamentChallongeDAO
                                    )(implicit ec: ExecutionContext,
                                      system: ActorSystem,
                                      mat: Materializer)
  extends SilhouetteController(components) with I18nSupport with RequestMarkerContext {

  private val logger = Logger(getClass)

  val voteForm: Form[VoteFormData] = Form(
    mapping(
      "optionId" -> number,
      "confidence" -> number(min = 1),
      "eventId" -> number,
      "pollId" -> number
    )(VoteFormData.apply)(nn => Some(nn.optionId, nn.confidence, nn.eventId, nn.pollId))
  )

  def userEvents: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    val userId = request.identity.userId

    val webSocketUrl = routes.UserEventsController.eventsUpdates.webSocketURL()

    for {
      userStreamerStates <- userStreamerStateRepository.getByUserId(userId)
      channelIds = userStreamerStates.map(_.streamerChannelId)
      channelBalanceMap = userStreamerStates.map(uss => uss.streamerChannelId -> uss.currentBalanceNumber).toMap
      events <- Future.sequence(channelIds.map(streamerEventRepository.getActiveEventsByChannel))
      flatEvents = events.flatten
      frontalEvents = flatEvents.flatMap(FrontalStreamerEvent.apply)
      frontalEventsComplete <- Future.sequence(frontalEvents.map(pollService.completeFrontalPoll))


      allActiveEvents <- streamerEventRepository.getAllActiveEvents()
      userEventIds = flatEvents.flatMap(_.eventId).toSet
      extraActiveEvents = allActiveEvents.filter(event =>
        event.eventId.isDefined && !userEventIds.contains(event.eventId.get))
      extraActiveEventsWithFrontal = extraActiveEvents.flatMap(FrontalStreamerEvent.apply)

      openTournaments <- tournamentService.getOpenTournaments

      userRegistrations <- Future.sequence(openTournaments.map { tournament =>
        tournamentService.isUserRegistered(tournament.id, userId).map(tournament -> _)
      })
      tournamentsWithRegistrationStatus = userRegistrations.toMap

      inProgressTournaments <- tournamentService.getTournamentsByStatus(TournamentStatus.InProgress)
      userMatches <- getUserMatches(userId, inProgressTournaments)

    } yield {
      Ok(views.html.userEvents(
        frontalEventsComplete,
        channelBalanceMap,
        voteForm,
        request.identity,
        extraActiveEventsWithFrontal,
        webSocketUrl,
        openTournaments,
        tournamentsWithRegistrationStatus,
        userMatches
      ))
    }
  }

  private def getUserMatches(userId: Long, tournaments: List[Tournament]): Future[List[UserMatchInfo]] = {
    val tournamentsWithChallongeIds = tournaments.filter(_.challongeTournamentId.isDefined)

    val matchFutures = tournamentsWithChallongeIds.map { tournament =>
      val challongeTournamentId = tournament.challongeTournamentId.get

      for {
        participantOpt <- tournamentChallongeDAO.getTournamentChallongeParticipants(tournament.id)
          .map(_.find(_.userId == userId))
        matches <- participantOpt match {
          case Some(participant) =>
            tournamentChallongeService.getMatchesForParticipant(challongeTournamentId, participant.challongeParticipantId)
              .map(_.map(challengeMatch => UserMatchInfo(
                tournament = tournament,
                matchId = challengeMatch.id.toString,
                challengeMatchId = challengeMatch.id,
                opponent = challengeMatch.opponent,
                status = challengeMatch.state,
                scheduledTime = challengeMatch.scheduledTime,
                winnerId = challengeMatch.winnerId
              )))
          case None =>
            Future.successful(List.empty[UserMatchInfo])
        }
      } yield matches
    }

    Future.sequence(matchFutures).map(_.flatten)
  }

  def eventsUpdates: WebSocket = WebSocket.acceptOrResult[String, String] { request =>
    Future.successful {
      if (sameOriginCheck(request)) {
        Right(eventUpdateService.eventsFlow())
      } else {
        Left(Forbidden("Forbidden"))
      }
    }
  }

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

  private def originMatches(origin: String): Boolean = {
    try {
      val url = new URI(origin)
      (url.getHost == "localhost" || url.getHost == "evolutioncomplete.com" || url.getHost == "91.99.13.219") &&
        (url.getPort match {
          case 9000 | 5000 | 19001 => true;
          case _ => false
        })
    } catch {
      case e: Exception => false
    }
  }

  def submitVote: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    voteForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(Redirect(routes.UserEventsController.userEvents())
          .flashing("error" -> "Invalid form submission"))
      },
      voteData => {
        val userId = request.identity.userId

        (for {
          event <- streamerEventRepository.getById(voteData.eventId)

          result <- event match {
            case Some(evt) if evt.endTime.isEmpty =>
              pollService.registerPollVote(
                voteData.pollId,
                voteData.optionId,
                userId,
                None,
                voteData.confidence,
                evt.channelId
              ).map(_ =>
                Redirect(routes.UserEventsController.userEvents())
                  .flashing("success" -> "Vote registered successfully")
              )

            case Some(_) =>
              Future.successful(
                Redirect(routes.UserEventsController.userEvents())
                  .flashing("error" -> "This event is no longer accepting votes")
              )

            case None =>
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

  def joinEvent(eventId: Int): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    val userId = request.identity.userId

    (for {
      eventOpt <- streamerEventRepository.getById(eventId)

      result <- eventOpt match {
        case Some(event) =>
          userStreamerStateRepository.getUserStreamerBalance(userId, event.channelId).flatMap {
            existingBalance =>
              if (existingBalance.isEmpty) {
                userStreamerStateRepository.create(userId, event.channelId).map { _ =>
                  Redirect(routes.UserEventsController.userEvents())
                    .flashing("success" -> s"Te uniste al evento: ${event.eventName}")
                }
              } else {
                Future.successful(
                  Redirect(routes.UserEventsController.userEvents())
                    .flashing("info" -> "Ya eras parte del evento")
                )
              }
          }


        case None =>
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

  def registerForTournament(tournamentId: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
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

case class VoteFormData(optionId: Int, confidence: Int, eventId: Int, pollId: Int)
