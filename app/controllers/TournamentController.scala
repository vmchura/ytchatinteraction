package controllers

import forms.{Forms, TournamentCreateForm}
import models.{Tournament, TournamentStatus}
import models.repository.TournamentRepository
import services.TournamentService
import play.api.Logger
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.*
import org.webjars.play.WebJarsUtil

import java.time.{Instant, ZoneOffset}
import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

/**
 * Controller for tournament management.
 */
@Singleton
class TournamentController @Inject()(val controllerComponents: ControllerComponents,
                                   tournamentRepository: TournamentRepository,
                                   tournamentService: TournamentService)
                                  (implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
  extends BaseController with I18nSupport {

  private val logger = Logger(getClass)

  /**
   * Shows the tournament creation form.
   */
  def showCreateForm(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.tournamentCreate(Forms.tournamentCreateForm))
  }

  /**
   * Shows all open tournaments with their registered users and start buttons.
   */
  def showOpenTournaments(): Action[AnyContent] = Action.async { implicit request =>
    val future = for {
      openTournaments <- tournamentService.getTournamentsByStatus(TournamentStatus.RegistrationOpen)
      inProgressTournaments <- tournamentService.getTournamentsByStatus(TournamentStatus.InProgress)
      allTournaments = openTournaments ++ inProgressTournaments
      // Get registrations with users for each tournament
      tournamentWithRegistrations <- Future.sequence(allTournaments.map { tournament =>
        tournamentService.getTournamentRegistrationsWithUsers(tournament.id).map { registrations =>
          (tournament, registrations)
        }
      })
    } yield {
      Ok(views.html.tournamentsList(tournamentWithRegistrations))
    }
    
    future.recover {
      case ex =>
        logger.error(s"Error loading tournaments: ${ex.getMessage}", ex)
        InternalServerError("Error loading tournaments. Please try again.")
    }
  }

  /**
   * Starts a tournament by changing its status from RegistrationOpen to InProgress.
   */
  def startTournament(id: Long): Action[AnyContent] = Action.async { implicit request =>
    val future = for {
      tournamentOpt <- tournamentService.getTournament(id)
      result <- tournamentOpt match {
        case Some(tournament) if tournament.status == TournamentStatus.RegistrationOpen =>
          // Update tournament with start time and status
          val updatedTournament = tournament.copy(
            status = TournamentStatus.InProgress,
            tournamentStartAt = Some(Instant.now()),
            updatedAt = Instant.now()
          )
          tournamentService.updateTournament(updatedTournament).map {
            case Some(updated) =>
              logger.info(s"Started tournament: ${updated.name} (ID: ${updated.id})")
              Redirect(routes.TournamentController.showOpenTournaments())
                .flashing("success" -> s"Tournament '${updated.name}' has been started!")
            case None =>
              logger.warn(s"Failed to update tournament with ID $id")
              Redirect(routes.TournamentController.showOpenTournaments())
                .flashing("error" -> "Failed to start tournament. Please try again.")
          }
        case Some(tournament) =>
          logger.warn(s"Tournament ${tournament.name} (ID: $id) is not open for registration (status: ${tournament.status})")
          Future.successful(
            Redirect(routes.TournamentController.showOpenTournaments())
              .flashing("error" -> "Tournament is not open for registration and cannot be started.")
          )
        case None =>
          logger.warn(s"Tournament with ID $id not found")
          Future.successful(
            Redirect(routes.TournamentController.showOpenTournaments())
              .flashing("error" -> "Tournament not found.")
          )
      }
    } yield result
    
    future.recover {
      case ex =>
        logger.error(s"Error starting tournament $id: ${ex.getMessage}", ex)
        Redirect(routes.TournamentController.showOpenTournaments())
          .flashing("error" -> "Error starting tournament. Please try again.")
    }
  }

  /**
   * Creates a new tournament with only the name provided.
   * All other fields will be set to default values.
   */
  def createTournament(): Action[AnyContent] = Action.async { implicit request =>
    Forms.tournamentCreateForm.bindFromRequest().fold(
      formWithErrors => {
        logger.warn(s"Tournament creation form has errors: ${formWithErrors.errors}")
        Future.successful(BadRequest(views.html.tournamentCreate(formWithErrors)))
      },
      tournamentData => {
        // Create tournament with default values
        val now = Instant.now()
        val defaultTournament = Tournament(
          name = tournamentData.name.trim,
          description = None,
          maxParticipants = 16, // Default max participants
          registrationStartAt = now,
          registrationEndAt = now.plusSeconds(7 * 24 * 3600), // 7 days from now
          tournamentStartAt = None,
          tournamentEndAt = None,
          challongeTournamentId = None,
          status = models.TournamentStatus.RegistrationOpen,
          createdAt = now,
          updatedAt = now
        )

        tournamentRepository.create(defaultTournament).map { createdTournament =>
          logger.info(s"Created tournament: ${createdTournament.name} with ID: ${createdTournament.id}")
          Redirect(routes.TournamentController.showCreateForm())
            .flashing("success" -> s"Tournament '${createdTournament.name}' created successfully!")
        }.recover {
          case ex =>
            logger.error(s"Error creating tournament: ${ex.getMessage}", ex)
            InternalServerError(views.html.tournamentCreate(Forms.tournamentCreateForm.fill(tournamentData)))
              .flashing("error" -> "Error creating tournament. Please try again.")
        }
      }
    )
  }
}
