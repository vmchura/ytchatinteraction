package controllers

import forms.Forms
import models.{Tournament, TournamentStatus}
import models.dao.TournamentChallongeDAO
import models.repository.{TournamentRepository, UserAvailabilityRepository}
import models.viewmodels.{MatchSchedulingViewData, PlayerSchedulingInfo, TournamentMatchDisplay}
import services.PotentialMatchTime
import modules.DefaultEnv
import services.PotentialMatchCalculator
import services.{
  ContentCreatorChannelService,
  TournamentService,
  TournamentChallongeService
}
import services.{TournamentChallongeConfiguration}

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
class TournamentController @Inject() (
    val controllerComponents: ControllerComponents,
    tournamentRepository: TournamentRepository,
    tournamentService: TournamentService,
    tournamentChallongeService: TournamentChallongeService,
    contentCreatorChannelService: ContentCreatorChannelService,
    tournamentChallongeDAO: TournamentChallongeDAO,
    userAvailabilityRepository: UserAvailabilityRepository,
    silhouette: Silhouette[DefaultEnv]
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil)
    extends BaseController
    with I18nSupport {

  private val logger = Logger(getClass)

  def showCreateForm(): Action[AnyContent] =
    silhouette.SecuredAction(WithAdmin()).async { implicit request =>
      contentCreatorChannelService.getAllContentCreatorChannels().map {
        activeChannels =>
          Ok(
            views.html.tournamentCreate(
              Forms.tournamentCreateForm,
              activeChannels.filter(_.isActive)
            )
          )
      }
    }

  def showOpenTournaments(): Action[AnyContent] =
    silhouette.SecuredAction(WithAdmin()).async { implicit request =>
      val future = for {
        openTournaments <- tournamentService.getTournamentsByStatus(
          TournamentStatus.RegistrationOpen
        )
        closedRegistrationTournaments <- tournamentService
          .getTournamentsByStatus(TournamentStatus.RegistrationClosed)
        inProgressTournaments <- tournamentService.getTournamentsByStatus(
          TournamentStatus.InProgress
        )
        completedTournaments <- tournamentService.getTournamentsByStatus(
          TournamentStatus.Completed
        )
        cancelledTournaments <- tournamentService.getTournamentsByStatus(
          TournamentStatus.Cancelled
        )
        allTournaments =
          openTournaments ++ closedRegistrationTournaments ++ inProgressTournaments ++ completedTournaments ++ cancelledTournaments
        tournamentWithRegistrations <- Future.sequence(allTournaments.map {
          tournament =>
            tournamentService
              .getTournamentRegistrationsWithUsers(tournament.id)
              .map { registrations =>
                (tournament, registrations)
              }
        })
      } yield {
        Ok(views.html.tournamentManagement(tournamentWithRegistrations))
      }

      future.recover { case ex =>
        logger.error(s"Error loading tournaments: ${ex.getMessage}", ex)
        InternalServerError("Error loading tournaments. Please try again.")
      }
    }

  def manageTournament(id: Long): Action[AnyContent] =
    silhouette.SecuredAction(WithAdmin()).async { implicit request =>
      val future = for {
        tournamentOpt <- tournamentService.getTournament(id)
        result <- tournamentOpt match {
          case Some(tournament) =>
            for {
              registrationsWithUsers <- tournamentService
                .getTournamentRegistrationsWithUsers(id)
              matches <-
                if (
                  tournament.status == TournamentStatus.InProgress && tournament.challongeTournamentId.isDefined
                ) {
                  tournamentChallongeService
                    .getMatchesWithParticipants(
                      tournament.challongeTournamentId.get
                    )
                    .map { matchesWithParticipants =>
                      matchesWithParticipants.map {
                        case (challongeMatch, participantMap) =>
                          TournamentMatchDisplay.fromChallongeMatch(
                            challongeMatch,
                            participantMap
                          )
                      }
                    }
                } else {
                  Future.successful(List.empty[TournamentMatchDisplay])
                }
            } yield {
              Ok(
                views.html.tournamentManagementDetail(
                  tournament,
                  registrationsWithUsers,
                  matches
                )
              )
            }
          case None =>
            Future.successful(
              Redirect(routes.TournamentController.showOpenTournaments())
                .flashing("error" -> "Tournament not found.")
            )
        }
      } yield result

      future.recover { case ex =>
        logger.error(s"Error loading tournament $id: ${ex.getMessage}", ex)
        Redirect(routes.TournamentController.showOpenTournaments())
          .flashing(
            "error" -> "Error loading tournament details. Please try again."
          )
      }
    }

  def showMatchScheduling(tournamentId: Long, matchId: Long): Action[AnyContent] =
    silhouette.SecuredAction(WithAdmin()).async { implicit request =>
      val future = for {
        tournamentOpt <- tournamentService.getTournament(tournamentId)
        result <- tournamentOpt match {
          case Some(tournament) if tournament.challongeTournamentId.isDefined =>
            val challongeTournamentId = tournament.challongeTournamentId.get
            for {
              // Fetch match details from Challonge
              matchOpt <- tournamentChallongeService.getMatch(challongeTournamentId, matchId)
              result <- matchOpt match {
                case Some(challongeMatch) if challongeMatch.state == "open" =>
                  // Map Challonge participant IDs to user IDs
                  val player1MappingFut = challongeMatch.player1Id match {
                    case Some(pid) => tournamentChallongeDAO.getTournamentUserByChallongeParticipantId(pid)
                    case None => Future.successful(None)
                  }
                  val player2MappingFut = challongeMatch.player2Id match {
                    case Some(pid) => tournamentChallongeDAO.getTournamentUserByChallongeParticipantId(pid)
                    case None => Future.successful(None)
                  }
                  
                  for {
                    player1Mapping <- player1MappingFut
                    player2Mapping <- player2MappingFut
                    result <- (player1Mapping, player2Mapping) match {
                      case (Some((_, user1)), Some((_, user2))) =>
                        // Fetch timezones and availabilities for both users
                        for {
                          timezone1Opt <- userAvailabilityRepository.getTimezone(user1.userId)
                          timezone2Opt <- userAvailabilityRepository.getTimezone(user2.userId)
                          availabilities1 <- userAvailabilityRepository.getAllAvailabilitiesByUserId(user1.userId)
                          availabilities2 <- userAvailabilityRepository.getAllAvailabilitiesByUserId(user2.userId)
                        } yield {
                          (timezone1Opt, timezone2Opt) match {
                            case (Some(tz1), Some(tz2)) =>
                              // Calculate optimal match times
                              val suggestedTimes = PotentialMatchCalculator.findOptimalMatchTimes(
                                availabilities1,
                                availabilities2,
                                tz1.timezone,
                                tz2.timezone,
                                Instant.now()
                              )
                              
                              val viewData = MatchSchedulingViewData(
                                tournamentId = tournamentId,
                                matchId = matchId,
                                player1 = PlayerSchedulingInfo(
                                  userId = user1.userId,
                                  userName = user1.userName,
                                  timezone = tz1.timezone,
                                  hasAvailability = availabilities1.nonEmpty
                                ),
                                player2 = PlayerSchedulingInfo(
                                  userId = user2.userId,
                                  userName = user2.userName,
                                  timezone = tz2.timezone,
                                  hasAvailability = availabilities2.nonEmpty
                                ),
                                suggestedTimes = suggestedTimes
                              )
                              Ok(views.html.matchScheduling(viewData))
                            case (None, _) =>
                              Redirect(routes.TournamentController.manageTournament(tournamentId))
                                .flashing("error" -> s"Player ${user1.userName} has not set their timezone.")
                            case (_, None) =>
                              Redirect(routes.TournamentController.manageTournament(tournamentId))
                                .flashing("error" -> s"Player ${user2.userName} has not set their timezone.")
                          }
                        }
                      case _ =>
                        Future.successful(
                          Redirect(routes.TournamentController.manageTournament(tournamentId))
                            .flashing("error" -> "Could not find player mappings for this match.")
                        )
                    }
                  } yield result
                case Some(_) =>
                  Future.successful(
                    Redirect(routes.TournamentController.manageTournament(tournamentId))
                      .flashing("error" -> "Match is not open for scheduling.")
                  )
                case None =>
                  Future.successful(
                    Redirect(routes.TournamentController.manageTournament(tournamentId))
                      .flashing("error" -> "Match not found.")
                  )
              }
            } yield result
          case Some(_) =>
            Future.successful(
              Redirect(routes.TournamentController.manageTournament(tournamentId))
                .flashing("error" -> "Tournament is not linked to Challonge.")
            )
          case None =>
            Future.successful(
              Redirect(routes.TournamentController.showOpenTournaments())
                .flashing("error" -> "Tournament not found.")
            )
        }
      } yield result
      
      future.recover { case ex =>
        logger.error(s"Error loading match scheduling for tournament $tournamentId, match $matchId: ${ex.getMessage}", ex)
        Redirect(routes.TournamentController.manageTournament(tournamentId))
          .flashing("error" -> "Error loading match scheduling. Please try again.")
      }
    }

  def startTournament(id: Long): Action[AnyContent] =
    silhouette.SecuredAction(WithAdmin()).async { implicit request =>
      // Bind the configuration form
      val configForm = Forms.tournamentChallongeConfigForm.bindFromRequest()
      val config = configForm.fold(
        errors => {
          // If form has errors, use default configuration
          logger.warn(
            s"Invalid tournament configuration form: ${errors.errors}, using defaults"
          )
          TournamentChallongeConfiguration()
        },
        validConfig => TournamentChallongeConfiguration.fromForm(validConfig)
      )
      val future = for {
        tournamentOpt <- tournamentService.getTournament(id)
        result <- tournamentOpt match {
          case Some(tournament)
              if tournament.status == TournamentStatus.RegistrationOpen =>
            for {
              registrationsWithUsers <- tournamentService
                .getTournamentRegistrationsWithUsers(id)
              participants = registrationsWithUsers.map(_._2)

              result <- tournamentChallongeService
                .createChallongeTournament(tournament, participants, config)
                .flatMap { case (challongeTournamentId, challongeUrl) =>
                  logger.info(
                    s"Created Challonge tournament with ID: $challongeTournamentId for tournament: ${tournament.name}"
                  )

                  val updatedTournament = tournament.copy(
                    status = TournamentStatus.InProgress,
                    tournamentStartAt = Some(Instant.now()),
                    challongeTournamentId = Some(challongeTournamentId),
                    challongeUrl = Some(challongeUrl),
                    updatedAt = Instant.now()
                  )

                  tournamentService
                    .updateTournament(updatedTournament)
                    .flatMap {
                      case Some(updated) =>
                        logger.info(
                          s"Started tournament: ${updated.name} (ID: ${updated.id}) with Challonge ID: $challongeTournamentId"
                        )

                        tournamentChallongeService
                          .startChallongeTournament(challongeTournamentId)
                          .map { started =>
                            if (started) {
                              logger.info(
                                s"Successfully started Challonge tournament $challongeTournamentId"
                              )
                              val realParticipants = participants.length
                              val totalParticipants = realParticipants

                              val successMessage =
                                s"Tournament '${updated.name}' has been started in Challonge with $totalParticipants participants!"

                              Redirect(
                                routes.TournamentController
                                  .showOpenTournaments()
                              )
                                .flashing("success" -> successMessage)
                            } else {
                              logger.warn(
                                s"Failed to start Challonge tournament $challongeTournamentId"
                              )
                              Redirect(
                                routes.TournamentController
                                  .showOpenTournaments()
                              )
                                .flashing(
                                  "warning" -> s"Tournament '${updated.name}' was created in Challonge but failed to start. You may need to start it manually."
                                )
                            }
                          }
                      case None =>
                        logger.error(
                          s"Failed to update tournament with ID $id after creating Challonge tournament"
                        )
                        Future.successful(
                          Redirect(
                            routes.TournamentController.showOpenTournaments()
                          )
                            .flashing(
                              "error" -> "Failed to update tournament after creating in Challonge. Please try again."
                            )
                        )
                    }
                }
                .recover { case ex =>
                  logger.error(
                    s"Failed to create Challonge tournament for ${tournament.name}: ${ex.getMessage}",
                    ex
                  )
                  Redirect(routes.TournamentController.showOpenTournaments())
                    .flashing(
                      "error" -> s"Failed to create tournament in Challonge: ${ex.getMessage}"
                    )
                }
            } yield result
          case Some(tournament) =>
            logger.warn(
              s"Tournament ${tournament.name} (ID: $id) is not open for registration (status: ${tournament.status})"
            )
            Future.successful(
              Redirect(routes.TournamentController.showOpenTournaments())
                .flashing(
                  "error" -> "Tournament is not open for registration and cannot be started."
                )
            )
          case None =>
            logger.warn(s"Tournament with ID $id not found")
            Future.successful(
              Redirect(routes.TournamentController.showOpenTournaments())
                .flashing("error" -> "Tournament not found.")
            )
        }
      } yield result

      future.recover { case ex =>
        logger.error(s"Error starting tournament $id: ${ex.getMessage}", ex)
        Redirect(routes.TournamentController.showOpenTournaments())
          .flashing("error" -> "Error starting tournament. Please try again.")
      }
    }

  def completeTournament(id: Long): Action[AnyContent] =
    silhouette.SecuredAction(WithAdmin()).async { implicit request =>
      val future = for {
        tournamentOpt <- tournamentService.getTournament(id)
        result <- tournamentOpt match {
          case Some(tournament)
              if tournament.status == TournamentStatus.InProgress ||
                tournament.status == TournamentStatus.RegistrationOpen ||
                tournament.status == TournamentStatus.RegistrationClosed =>
            val updatedTournament = tournament.copy(
              status = TournamentStatus.Completed,
              tournamentEndAt = Some(Instant.now()),
              updatedAt = Instant.now()
            )

            tournamentService.updateTournament(updatedTournament).map {
              case Some(updated) =>
                logger.info(
                  s"Completed tournament: ${updated.name} (ID: ${updated.id})"
                )
                Redirect(routes.TournamentController.showOpenTournaments())
                  .flashing(
                    "success" -> s"Tournament '${updated.name}' has been marked as completed!"
                  )
              case None =>
                logger.error(s"Failed to update tournament with ID $id")
                Redirect(routes.TournamentController.showOpenTournaments())
                  .flashing(
                    "error" -> "Failed to update tournament. Please try again."
                  )
            }
          case Some(tournament) =>
            logger.warn(
              s"Tournament ${tournament.name} (ID: $id) cannot be completed (current status: ${tournament.status})"
            )
            Future.successful(
              Redirect(routes.TournamentController.showOpenTournaments())
                .flashing(
                  "error" -> "Tournament cannot be completed from its current status."
                )
            )
          case None =>
            logger.warn(s"Tournament with ID $id not found")
            Future.successful(
              Redirect(routes.TournamentController.showOpenTournaments())
                .flashing("error" -> "Tournament not found.")
            )
        }
      } yield result

      future.recover { case ex =>
        logger.error(s"Error completing tournament $id: ${ex.getMessage}", ex)
        Redirect(routes.TournamentController.showOpenTournaments())
          .flashing("error" -> "Error completing tournament. Please try again.")
      }
    }

  def cancelTournament(id: Long): Action[AnyContent] =
    silhouette.SecuredAction(WithAdmin()).async { implicit request =>
      val future = for {
        tournamentOpt <- tournamentService.getTournament(id)
        result <- tournamentOpt match {
          case Some(tournament)
              if tournament.status == TournamentStatus.InProgress ||
                tournament.status == TournamentStatus.RegistrationOpen ||
                tournament.status == TournamentStatus.RegistrationClosed =>
            val updatedTournament = tournament.copy(
              status = TournamentStatus.Cancelled,
              tournamentEndAt = Some(Instant.now()),
              updatedAt = Instant.now()
            )

            tournamentService.updateTournament(updatedTournament).map {
              case Some(updated) =>
                logger.info(
                  s"Cancelled tournament: ${updated.name} (ID: ${updated.id})"
                )
                Redirect(routes.TournamentController.showOpenTournaments())
                  .flashing(
                    "success" -> s"Tournament '${updated.name}' has been cancelled!"
                  )
              case None =>
                logger.error(s"Failed to update tournament with ID $id")
                Redirect(routes.TournamentController.showOpenTournaments())
                  .flashing(
                    "error" -> "Failed to update tournament. Please try again."
                  )
            }
          case Some(tournament) =>
            logger.warn(
              s"Tournament ${tournament.name} (ID: $id) cannot be cancelled (current status: ${tournament.status})"
            )
            Future.successful(
              Redirect(routes.TournamentController.showOpenTournaments())
                .flashing(
                  "error" -> "Tournament cannot be cancelled from its current status."
                )
            )
          case None =>
            logger.warn(s"Tournament with ID $id not found")
            Future.successful(
              Redirect(routes.TournamentController.showOpenTournaments())
                .flashing("error" -> "Tournament not found.")
            )
        }
      } yield result

      future.recover { case ex =>
        logger.error(s"Error cancelling tournament $id: ${ex.getMessage}", ex)
        Redirect(routes.TournamentController.showOpenTournaments())
          .flashing("error" -> "Error cancelling tournament. Please try again.")
      }
    }

  def createTournament(): Action[AnyContent] =
    silhouette.SecuredAction(WithAdmin()).async { implicit request =>
      Forms.tournamentCreateForm
        .bindFromRequest()
        .fold(
          formWithErrors => {
            logger.warn("Tournament creation failed: form validation errors")
            contentCreatorChannelService.getAllContentCreatorChannels().map {
              channels =>
                BadRequest(
                  views.html.tournamentCreate(
                    formWithErrors,
                    channels.filter(_.isActive)
                  )
                )
            }
          },
          tournamentData => {
            logger
              .info(s"Creating tournament with name: ${tournamentData.name}")

            val now = Instant.now()
            val defaultTournament = Tournament(
              name = tournamentData.name.trim,
              description = None,
              maxParticipants = 16,
              registrationStartAt = now,
              registrationEndAt = now.plusSeconds(7 * 24 * 3600),
              tournamentCode = tournamentData.code,
              tournamentStartAt = None,
              tournamentEndAt = None,
              challongeTournamentId = None,
              contentCreatorChannelId = tournamentData.contentCreatorChannelId,
              status = models.TournamentStatus.RegistrationOpen,
              createdAt = now,
              updatedAt = now
            )

            tournamentRepository
              .create(defaultTournament)
              .map { createdTournament =>
                logger.info(
                  s"Created tournament: ${createdTournament.name} with ID: ${createdTournament.id}"
                )
                Redirect(routes.TournamentController.showCreateForm())
                  .flashing(
                    "success" -> s"Tournament '${createdTournament.name}' created successfully!"
                  )
              }
              .recoverWith { case ex =>
                logger.error(s"Error creating tournament: ${ex.getMessage}", ex)
                // Get channels for error view
                contentCreatorChannelService
                  .getAllContentCreatorChannels()
                  .map { channels =>
                    InternalServerError(
                      views.html.tournamentCreate(
                        Forms.tournamentCreateForm.fill(tournamentData),
                        channels.filter(_.isActive)
                      )
                    )
                      .flashing(
                        "error" -> "Error creating tournament. Please try again."
                      )
                  }
              }
          }
        )
    }
}
