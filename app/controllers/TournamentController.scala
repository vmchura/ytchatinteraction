package controllers

import forms.{Forms, TournamentCreateForm}
import models.{Tournament, TournamentStatus}
import models.repository.TournamentRepository
import services.{TournamentService, TournamentChallongeService}
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
                                     tournamentService: TournamentService,
                                     tournamentChallongeService: TournamentChallongeService)
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
   * Creates a tournament in Challonge with all registered users and updates the local tournament with the Challonge ID.
   */
  def startTournament(id: Long): Action[AnyContent] = Action.async { implicit request =>
    val future = for {
      tournamentOpt <- tournamentService.getTournament(id)
      result <- tournamentOpt match {
        case Some(tournament) if tournament.status == TournamentStatus.RegistrationOpen =>
          // Get registered users for the tournament
          for {
            registrationsWithUsers <- tournamentService.getTournamentRegistrationsWithUsers(id)
            participants = registrationsWithUsers.map(_._2) // Extract just the users
            result <- if (participants.isEmpty) {
              logger.warn(s"Cannot start tournament ${tournament.name} (ID: $id) - no participants registered")
              Future.successful(
                Redirect(routes.TournamentController.showOpenTournaments())
                  .flashing("error" -> "Cannot start tournament with no participants.")
              )
            } else {
              // Create tournament in Challonge
              tournamentChallongeService.createChallongeTournament(tournament, participants).flatMap { challongeTournamentId =>
                logger.info(s"Created Challonge tournament with ID: $challongeTournamentId for tournament: ${tournament.name}")

                // Update local tournament with Challonge ID, start time and status
                val updatedTournament = tournament.copy(
                  status = TournamentStatus.InProgress,
                  tournamentStartAt = Some(Instant.now()),
                  challongeTournamentId = Some(challongeTournamentId),
                  updatedAt = Instant.now()
                )

                tournamentService.updateTournament(updatedTournament).flatMap {
                  case Some(updated) =>
                    logger.info(s"Started tournament: ${updated.name} (ID: ${updated.id}) with Challonge ID: $challongeTournamentId")

                    // Start the tournament in Challonge
                    tournamentChallongeService.startChallongeTournament(challongeTournamentId).map { started =>
                      if (started) {
                        logger.info(s"Successfully started Challonge tournament $challongeTournamentId")
                        Redirect(routes.TournamentController.showOpenTournaments())
                          .flashing("success" -> s"Tournament '${updated.name}' has been started in Challonge with ${participants.length} participants!")
                      } else {
                        logger.warn(s"Failed to start Challonge tournament $challongeTournamentId")
                        Redirect(routes.TournamentController.showOpenTournaments())
                          .flashing("warning" -> s"Tournament '${updated.name}' was created in Challonge but failed to start. You may need to start it manually.")
                      }
                    }
                  case None =>
                    logger.error(s"Failed to update tournament with ID $id after creating Challonge tournament")
                    Future.successful(
                      Redirect(routes.TournamentController.showOpenTournaments())
                        .flashing("error" -> "Failed to update tournament after creating in Challonge. Please try again.")
                    )
                }
              }.recover {
                case ex =>
                  logger.error(s"Failed to create Challonge tournament for ${tournament.name}: ${ex.getMessage}", ex)
                  Redirect(routes.TournamentController.showOpenTournaments())
                    .flashing("error" -> s"Failed to create tournament in Challonge: ${ex.getMessage}")
              }
            }
          } yield result
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
