package controllers

import forms.Forms
import models.{Tournament, TournamentStatus}
import models.repository.TournamentRepository
import modules.DefaultEnv
import services.{ContentCreatorChannelService, TournamentService, TournamentChallongeService}
import utils.auth.WithAdmin
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc.*
import play.silhouette.api.Silhouette
import org.webjars.play.WebJarsUtil

import java.time.Instant
import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TournamentController @Inject()(val controllerComponents: ControllerComponents,
                                     tournamentRepository: TournamentRepository,
                                     tournamentService: TournamentService,
                                     tournamentChallongeService: TournamentChallongeService,
                                     contentCreatorChannelService: ContentCreatorChannelService,
                                     silhouette: Silhouette[DefaultEnv])
                                    (implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
  extends BaseController with I18nSupport {

  private val logger = Logger(getClass)

  def showCreateForm(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    contentCreatorChannelService.getAllContentCreatorChannels().map { activeChannels =>
      Ok(views.html.tournamentCreate(Forms.tournamentCreateForm, activeChannels.filter(_.isActive)))
    }
  }

  def showOpenTournaments(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    val future = for {
      openTournaments <- tournamentService.getTournamentsByStatus(TournamentStatus.RegistrationOpen)
      inProgressTournaments <- tournamentService.getTournamentsByStatus(TournamentStatus.InProgress)
      allTournaments = openTournaments ++ inProgressTournaments
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

  def startTournament(id: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    val future = for {
      tournamentOpt <- tournamentService.getTournament(id)
      result <- tournamentOpt match {
        case Some(tournament) if tournament.status == TournamentStatus.RegistrationOpen =>
          for {
            registrationsWithUsers <- tournamentService.getTournamentRegistrationsWithUsers(id)
            participants = registrationsWithUsers.map(_._2)

            result <- tournamentChallongeService.createChallongeTournament(tournament, participants).flatMap { challongeTournamentId =>
              logger.info(s"Created Challonge tournament with ID: $challongeTournamentId for tournament: ${tournament.name}")

              val updatedTournament = tournament.copy(
                status = TournamentStatus.InProgress,
                tournamentStartAt = Some(Instant.now()),
                challongeTournamentId = Some(challongeTournamentId),
                updatedAt = Instant.now()
              )

              tournamentService.updateTournament(updatedTournament).flatMap {
                case Some(updated) =>
                  logger.info(s"Started tournament: ${updated.name} (ID: ${updated.id}) with Challonge ID: $challongeTournamentId")

                  tournamentChallongeService.startChallongeTournament(challongeTournamentId).map { started =>
                    if (started) {
                      logger.info(s"Successfully started Challonge tournament $challongeTournamentId")
                      val realParticipants = participants.length
                      val fakeParticipants = tournamentChallongeService.generateFakeUsers(participants).length
                      val totalParticipants = realParticipants + fakeParticipants

                      val successMessage = if (fakeParticipants > 0) {
                        s"Tournament '${updated.name}' has been started in Challonge with $realParticipants real participant(s) and $fakeParticipants fake participant(s) (total: $totalParticipants)!"
                      } else {
                        s"Tournament '${updated.name}' has been started in Challonge with $totalParticipants participants!"
                      }

                      Redirect(routes.TournamentController.showOpenTournaments())
                        .flashing("success" -> successMessage)
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

  def createTournament(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    Forms.tournamentCreateForm.bindFromRequest().fold(
      formWithErrors => {
        logger.warn("Tournament creation failed: form validation errors")
        contentCreatorChannelService.getAllContentCreatorChannels().map { channels =>
          BadRequest(views.html.tournamentCreate(formWithErrors, channels.filter(_.isActive)))
        }
      },
      tournamentData => {
        logger.info(s"Creating tournament with name: ${tournamentData.name}")

        val now = Instant.now()
        val defaultTournament = Tournament(
          name = tournamentData.name.trim,
          description = None,
          maxParticipants = 16,
          registrationStartAt = now,
          registrationEndAt = now.plusSeconds(7 * 24 * 3600),
          tournamentStartAt = None,
          tournamentEndAt = None,
          challongeTournamentId = None,
          contentCreatorChannelId = tournamentData.contentCreatorChannelId,
          status = models.TournamentStatus.RegistrationOpen,
          createdAt = now,
          updatedAt = now
        )

        tournamentRepository.create(defaultTournament).map { createdTournament =>
          logger.info(s"Created tournament: ${createdTournament.name} with ID: ${createdTournament.id}")
          Redirect(routes.TournamentController.showCreateForm())
            .flashing("success" -> s"Tournament '${createdTournament.name}' created successfully!")
        }.recoverWith {
          case ex =>
            logger.error(s"Error creating tournament: ${ex.getMessage}", ex)
            // Get channels for error view
            contentCreatorChannelService.getAllContentCreatorChannels().map { channels =>
              InternalServerError(views.html.tournamentCreate(Forms.tournamentCreateForm.fill(tournamentData), channels.filter(_.isActive)))
                .flashing("error" -> "Error creating tournament. Please try again.")
            }
        }
      }
    )
  }
}
