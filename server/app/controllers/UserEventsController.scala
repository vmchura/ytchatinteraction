package controllers

import javax.inject.*
import models.*
import models.viewmodels.UserEventViewData
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import play.api.Logger
import services.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import utils.auth.WithAdmin
import forms.Forms.registerToTournamentForm

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserEventsController @Inject() (
    components: DefaultSilhouetteControllerComponents,
    userEventDataService: UserEventDataService,
    tournamentMatchService: TournamentMatchService,
    votingService: VotingService,
    webSocketAuthService: WebSocketAuthService,
    eventUpdateService: EventUpdateService,
    userService: UserService
)(implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer)
    extends SilhouetteController(components)
    with I18nSupport
    with RequestMarkerContext {

  private val logger = Logger(getClass)

  def userEvents: Action[AnyContent] = silhouette.SecuredAction.async {
    implicit request =>
      val userId = request.identity.userId

      for {
        userEventData <- userEventDataService.getUserEventData(userId)
        tournamentData <- tournamentMatchService.getTournamentData(userId)
        userMatches <- tournamentMatchService.getUserMatches(
          userId,
          tournamentData.inProgressTournaments
        )
        userUpdatedHisAlias <- userService.hasSingleAliasHistory(userId)
      } yield {
        val viewData = UserEventViewData(
          userEvents = models.viewmodels.UserEventsData(
            activeEvents = userEventData.frontalEventsComplete,
            channelBalances = userEventData.channelBalanceMap,
            availableEvents = userEventData.extraActiveEventsWithFrontal
          ),
          tournaments = tournamentData,
          matches = userMatches,
          webSocketUrl =
            routes.UserEventsController.eventsUpdates.webSocketURL(),
          user = request.identity
        )

        Ok(views.html.userEvents(viewData, userUpdatedHisAlias))
      }
  }

  def eventsUpdates: WebSocket = WebSocket.acceptOrResult[String, String] {
    implicit request =>
      Future.successful {
        if (webSocketAuthService.validateWebSocketRequest) {
          Right(eventUpdateService.eventsFlow())
        } else {
          Left(Forbidden("Forbidden"))
        }
      }
  }

  def submitVote: Action[AnyContent] =
    silhouette.SecuredAction(WithAdmin()).async { implicit request =>
      votingService.parseVoteRequest match {
        case Some(voteData) =>
          votingService.processVote(voteData, request.identity.userId).map {
            case votingService.VoteResult.Success(message) =>
              Redirect(routes.UserEventsController.userEvents())
                .flashing("success" -> message)
            case votingService.VoteResult.Failure(message) =>
              Redirect(routes.UserEventsController.userEvents())
                .flashing("error" -> message)
          }

        case None =>
          Future.successful(
            Redirect(routes.UserEventsController.userEvents())
              .flashing("error" -> "Invalid form submission")
          )
      }
    }

  def joinEvent(eventId: Int): Action[AnyContent] =
    silhouette.SecuredAction(WithAdmin()).async { implicit request =>
      val userId = request.identity.userId

      userEventDataService.joinUserToEvent(userId, eventId)

      userEventDataService
        .joinUserToEvent(userId, eventId)
        .map {
          case Right(message) =>
            Redirect(routes.UserEventsController.userEvents())
              .flashing("success" -> message)
          case Left(error) =>
            Redirect(routes.UserEventsController.userEvents())
              .flashing("error" -> error)
        }
        .recover { case e: Exception =>
          logger.error(s"Error joining event $eventId for user $userId", e)
          Redirect(routes.UserEventsController.userEvents())
            .flashing("error" -> s"Error joining event: ${e.getMessage}")
        }
    }

  def registerForTournament(tournamentId: Long): Action[AnyContent] =
    silhouette.SecuredAction.async { implicit request =>
      val userId = request.identity.userId

      registerToTournamentForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            val errorMessage = formWithErrors.errors.map(_.message).mkString(", ")
            Future.successful(
              Redirect(routes.UserEventsController.userEvents())
                .flashing("error" -> s"Validation failed: $errorMessage")
            )
          },
          registerToTournamentData => {

            tournamentMatchService
              .registerUserForTournament(
                tournamentId,
                userId,
                Some(registerToTournamentData.code),
                Some(registerToTournamentData.race)
              )
              .map {
                case Right(_) =>
                  Redirect(routes.UserEventsController.userEvents())
                    .flashing(
                      "success" -> "Successfully registered for tournament!"
                    )
                case Left(error) =>
                  Redirect(routes.UserEventsController.userEvents())
                    .flashing("error" -> error)
              }
              .recover { case e: Exception =>
                logger.error(
                  s"Error registering user $userId for tournament $tournamentId",
                  e
                )
                Redirect(routes.UserEventsController.userEvents())
                  .flashing(
                    "error" -> s"Error registering for tournament: ${e.getMessage}"
                  )
              }
          }
        )
    }

  def tournamentRules: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.tournamentRules())
  }
}
