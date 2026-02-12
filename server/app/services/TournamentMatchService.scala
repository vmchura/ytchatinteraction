package services

import models.*
import models.dao.TournamentChallongeDAO
import models.repository.{ContentCreatorChannelRepository, YtUserRepository}
import models.viewmodels.*
import models.viewmodels.TournamentRegistrationUserStatus.*

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TournamentMatchService @Inject() (
    tournamentService: TournamentService,
    tournamentChallongeService: TournamentChallongeService,
    tournamentChallongeDAO: TournamentChallongeDAO,
    contentCreatorChannelRepository: ContentCreatorChannelRepository,
    ytUserRepository: YtUserRepository,
    registrationValidationService: TournamentRegistrationValidationService
)(implicit ec: ExecutionContext) {

  def getTournamentData(userId: Long): Future[TournamentViewDataForUser] = {
    for {
      openTournaments <- tournamentService.getOpenTournaments
      (registeredTuple, nonRegisteredTuple) <- Future
        .sequence(openTournaments.map { tournament =>
          tournamentService
            .isUserRegistered(tournament.id, userId)
            .map(tournament -> _)
        })
        .map(_.partition(_._2))
      registered = registeredTuple.map(_._1)
      nonRegistered = nonRegisteredTuple.map(_._1)
      inProgressTournaments <- tournamentService.getTournamentsByStatus(
        TournamentStatus.InProgress
      )
      // Get registration requirements for all races
      protossReq <- checkRaceRequirements(userId, "Protoss")
      zergReq <- checkRaceRequirements(userId, "Zerg")
      terranReq <- checkRaceRequirements(userId, "Terran")
      hasAvailability <- registrationValidationService.hasAvailabilityTimes(userId)
      hasTimezone <- registrationValidationService.hasTimezone(userId)
      result = buildTournamentViewData(
        registered,
        nonRegistered,
        inProgressTournaments,
        protossReq,
        zergReq,
        terranReq,
        hasAvailability,
        hasTimezone
      )
    } yield result
  }

  private def buildTournamentViewData(
      registered: List[Tournament],
      nonRegistered: List[Tournament],
      inProgressTournaments: List[Tournament],
      protossReq: RaceRegistrationRequirements,
      zergReq: RaceRegistrationRequirements,
      terranReq: RaceRegistrationRequirements,
      hasAvailability: Boolean,
      hasTimezone: Boolean
  ): TournamentViewDataForUser = {
    val requirementsPerRace = Map(
      "Protoss" -> protossReq,
      "Zerg" -> zergReq,
      "Terran" -> terranReq
    )
    val canRegisterAnyRace = requirementsPerRace.values.exists(_.hasEnoughReplays) && hasAvailability && hasTimezone
    
    TournamentViewDataForUser(
      registered.map(t =>
        TournamentOpenDataUser(
          t.id,
          t.name,
          TournamentRegistrationUserStatus.Registered,
          None,
          None
        )
      ) ++
        nonRegistered.map { t =>
          TournamentOpenDataUser(
            t.id,
            t.name,
            if (canRegisterAnyRace) TournamentRegistrationUserStatus.Unregistered else TournamentRegistrationUserStatus.NotAbleToRegister,
            None,
            Some(TournamentRegistrationRequirements(
              hasEnoughReplays = requirementsPerRace.values.exists(_.hasEnoughReplays),
              hasAvailability = hasAvailability,
              hasTimezone = hasTimezone,
              selectedRace = None,
              requirementsPerRace = requirementsPerRace
            ))
          )
        },
      inProgressTournaments.flatMap { t =>
        t.challongeUrl.zip(t.challongeTournamentId).map { case (url, id) =>
          InProgressTournament(t.id, t.name, id, url)
        }
      }
    )
  }

  private def checkRaceRequirements(userId: Long, race: String): Future[RaceRegistrationRequirements] = {
    import models.StarCraftModels.*
    val raceOpt = race match {
      case "Protoss" => Some(Protoss)
      case "Zerg"    => Some(Zerg)
      case "Terran"  => Some(Terran)
      case _         => None
    }

    raceOpt match {
      case None => Future.successful(RaceRegistrationRequirements(false, MatchupReplayCounts(0, 0, 0)))
      case Some(scRace) =>
        for {
          replayCounts <- registrationValidationService.getReplayCountsPerMatchup(userId, scRace)
        } yield {
          val matchupCounts = MatchupReplayCounts(
            vsProtoss = replayCounts.getOrElse(Protoss, 0),
            vsZerg = replayCounts.getOrElse(Zerg, 0),
            vsTerran = replayCounts.getOrElse(Terran, 0)
          )
          RaceRegistrationRequirements(
            hasEnoughReplays = matchupCounts.hasEnoughPerMatchup(2),
            replayCounts = matchupCounts
          )
        }
    }
  }

  def getUserMatches(
      userId: Long,
      tournaments: List[InProgressTournament]
  ): Future[List[UserMatchInfo]] = {

    val matchFutures = tournaments.map { tournament =>
      getUserMatchesForTournament(userId, tournament)
    }

    Future.sequence(matchFutures).map(_.flatten)
  }

  private def getUserMatchesForTournament(
      userId: Long,
      tournament: InProgressTournament
  ): Future[List[UserMatchInfo]] = {

    for {
      participantOpt <- tournamentChallongeDAO
        .getTournamentChallongeParticipants(tournament.id)
        .map(_.find(_.userId == userId))
      matches <- participantOpt match {
        case Some(participant) =>
          tournamentChallongeService
            .getMatchesForParticipant(
              tournament.challongeID,
              participant.challongeParticipantId
            )
            .map(
              _.map(challengeMatch =>
                UserMatchInfo(
                  tournament = tournament,
                  matchId = challengeMatch.id.toString,
                  challengeMatchId = challengeMatch.id,
                  opponent = challengeMatch.opponent,
                  status = challengeMatch.state,
                  scheduledTime = challengeMatch.scheduledTime,
                  winnerId = challengeMatch.winnerId,
                  winner = challengeMatch.winner
                )
              )
            )
        case None =>
          Future.successful(List.empty[UserMatchInfo])
      }
    } yield matches
  }

  def registerUserForTournament(
      tournamentId: Long,
      userId: Long,
      tournamentCode: String,
      race: StarCraftModels.SCRace
  ): Future[Either[String, Unit]] = {
    tournamentService.registerUser(tournamentId, userId, tournamentCode, race).map(_.map(_ => ()))
  }

  def isUserAbleToRegister(
      userId: Long,
      race: StarCraftModels.SCRace
  ): Future[Boolean] = {
    tournamentService.isUserAbleToRegister(userId, race)
  }

  def isUserRegisteredForTournament(
      tournamentId: Long,
      userId: Long
  ): Future[Boolean] = {
    tournamentService.isUserRegistered(tournamentId, userId)
  }

}
