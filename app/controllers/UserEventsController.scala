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
  
  def userEvents: Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    val userId = request.identity.userId

    for {
      userEventData <- getUserEventData(userId)
      tournamentData <- getTournamentData(userId)
      userMatches <- getUserMatches(userId, tournamentData.inProgressTournaments)
    } yield {
      Ok(views.html.userEvents(
        userEventData.frontalEventsComplete,
        userEventData.channelBalanceMap,
        request.identity,
        userEventData.extraActiveEventsWithFrontal,
        routes.UserEventsController.eventsUpdates.webSocketURL(),
        tournamentData.openTournaments,
        tournamentData.tournamentsWithRegistrationStatus,
        userMatches
      ))
    }
  }

  private case class UserEventData(
    frontalEventsComplete: Seq[FrontalStreamerEvent],
    channelBalanceMap: Map[String, Int],
    extraActiveEventsWithFrontal: Seq[FrontalStreamerEvent]
  )

  private case class TournamentData(
    openTournaments: List[Tournament],
    tournamentsWithRegistrationStatus: Map[Tournament, Boolean],
    inProgressTournaments: List[Tournament]
  )

  private def getUserEventData(userId: Long): Future[UserEventData] = {
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
    } yield UserEventData(frontalEventsComplete, channelBalanceMap, extraActiveEventsWithFrontal)
  }

  private def getTournamentData(userId: Long): Future[TournamentData] = {
    for {
      openTournaments <- tournamentService.getOpenTournaments
      userRegistrations <- Future.sequence(openTournaments.map { tournament =>
        tournamentService.isUserRegistered(tournament.id, userId).map(tournament -> _)
      })
      tournamentsWithRegistrationStatus = userRegistrations.toMap
      inProgressTournaments <- tournamentService.getTournamentsByStatus(TournamentStatus.InProgress)
    } yield TournamentData(openTournaments, tournamentsWithRegistrationStatus, inProgressTournaments)
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
      val allowedHosts = Set("localhost", "evolutioncomplete.com", "91.99.13.219")
      val allowedPorts = Set(9000, 5000, 19001)
      
      allowedHosts.contains(url.getHost) && allowedPorts.contains(url.getPort)
    } catch {
      case _: Exception => false
    }
  }

  def submitVote: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    val formData = request.body.asFormUrlEncoded

    val result = for {
      optionId <- getFormValue(formData, "optionId").flatMap(_.toIntOption)
      confidence <- getFormValue(formData, "confidence").flatMap(_.toIntOption)
      eventId <- getFormValue(formData, "eventId").flatMap(_.toIntOption)
      pollId <- getFormValue(formData, "pollId").flatMap(_.toIntOption)
    } yield VoteFormData(optionId, confidence, eventId, pollId)

    result match {
      case Some(voteData) => processVote(voteData, request.identity.userId)
      case None => Future.successful(
        Redirect(routes.UserEventsController.userEvents())
          .flashing("error" -> "Invalid form submission")
      )
    }
  }

  private def getFormValue(formData: Option[Map[String, Seq[String]]], key: String): Option[String] = {
    formData.flatMap(_.get(key)).flatMap(_.headOption)
  }

  private def processVote(voteData: VoteFormData, userId: Long): Future[Result] = {
    streamerEventRepository.getById(voteData.eventId).flatMap {
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
    }.recover {
      case e: Exception =>
        logger.error(s"Error registering vote for user $userId", e)
        Redirect(routes.UserEventsController.userEvents())
          .flashing("error" -> s"Error registering vote: ${e.getMessage}")
    }
  }

  def joinEvent(eventId: Int): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    val userId = request.identity.userId

    streamerEventRepository.getById(eventId).flatMap {
      case Some(event) =>
        userStreamerStateRepository.getUserStreamerBalance(userId, event.channelId).flatMap {
          case None =>
            userStreamerStateRepository.create(userId, event.channelId).map { _ =>
              Redirect(routes.UserEventsController.userEvents())
                .flashing("success" -> s"Te uniste al evento: ${event.eventName}")
            }
          case Some(_) =>
            Future.successful(
              Redirect(routes.UserEventsController.userEvents())
                .flashing("info" -> "Ya eras parte del evento")
            )
        }

      case None =>
        Future.successful(
          Redirect(routes.UserEventsController.userEvents())
            .flashing("error" -> "Event not found")
        )
    }.recover {
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
