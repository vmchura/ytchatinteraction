package controllers

import forms.{Forms, TournamentCreateForm}
import models.Tournament
import models.repository.TournamentRepository
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
                                   tournamentRepository: TournamentRepository)
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
