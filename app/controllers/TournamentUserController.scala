package controllers

import models.{User, Tournament, TournamentStatus, ChallongeMatch, UserMatchInfo}
import models.dao.TournamentChallongeDAO
import services.{TournamentService, TournamentChallongeService}
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc.*
import org.webjars.play.WebJarsUtil
import play.api.libs.json._

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

/**
 * Controller for tournament user actions - shows tournaments and matches assigned to the user.
 */
@Singleton
class TournamentUserController @Inject()(val controllerComponents: ControllerComponents,
                                          tournamentService: TournamentService,
                                          tournamentChallongeService: TournamentChallongeService,
                                          tournamentChallongeDAO: TournamentChallongeDAO)
                                         (implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
  extends BaseController with I18nSupport {

  private val logger = Logger(getClass)

  /**
   * Shows the user's tournament dashboard with open tournaments and assigned matches.
   * This is the main action for users to see their tournament status.
   */
  def dashboard(): Action[AnyContent] = Action.async { implicit request =>
    // For now, we'll use a mock user since authentication is not specified in the requirements
    // In a real application, this would come from the authenticated user session
    val mockUserId = 1L // This should be replaced with actual user authentication

    val future = for {
      // Get all open tournaments (tournaments that are still accepting registrations)
      openTournaments <- tournamentService.getTournamentsByStatus(TournamentStatus.RegistrationOpen)
      
      // Get in-progress tournaments (tournaments that have started)
      inProgressTournaments <- tournamentService.getTournamentsByStatus(TournamentStatus.InProgress)
      
      // Get user's assigned matches for in-progress tournaments
      userMatches <- getUserMatches(mockUserId, inProgressTournaments)
    } yield {
      Ok(views.html.tournamentUserDashboard(openTournaments, inProgressTournaments, userMatches))
    }

    future.recover {
      case ex =>
        logger.error(s"Error loading tournament dashboard: ${ex.getMessage}", ex)
        InternalServerError("Error loading tournament dashboard. Please try again.")
    }
  }

  /**
   * Gets matches assigned to a user from Challonge API for all in-progress tournaments.
   */
  private def getUserMatches(userId: Long, tournaments: List[Tournament]): Future[List[UserMatchInfo]] = {
    // Get all tournaments that have Challonge tournament IDs
    val tournamentsWithChallongeIds = tournaments.filter(_.challongeTournamentId.isDefined)
    
    // For each tournament, get the user's participant mapping and fetch matches
    val matchFutures = tournamentsWithChallongeIds.map { tournament =>
      val challongeTournamentId = tournament.challongeTournamentId.get
      
      for {
        // Get the user's Challonge participant ID
        participantOpt <- tournamentChallongeDAO.getTournamentChallongeParticipants(tournament.id)
          .map(_.find(_.userId == userId))
        matches <- participantOpt match {
          case Some(participant) =>
            // Get matches from Challonge API
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
    
    // Combine all matches from all tournaments
    Future.sequence(matchFutures).map(_.flatten)
  }
}
