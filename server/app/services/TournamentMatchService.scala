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
      // Get registration requirements for non-registered users
      registrationRequirements <- Future.sequence(
        Seq("Protoss", "Zerg", "Terran").map { race =>
          checkRegistrationRequirements(userId, race).map(race -> _)
        }
      ).map(_.toMap)
      hasAvailability <- registrationValidationService.hasAvailabilityTimes(userId)

    } yield TournamentViewDataForUser(
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
          // Determine if user can register based on requirements
          val canRegisterAnyRace = registrationRequirements.values.exists(_.hasEnoughReplays) && hasAvailability
          TournamentOpenDataUser(
            t.id,
            t.name,
            if (canRegisterAnyRace) TournamentRegistrationUserStatus.Unregistered else TournamentRegistrationUserStatus.NotAbleToRegister,
            None,
            Some(TournamentRegistrationRequirements(
              hasEnoughReplays = registrationRequirements.values.exists(_.hasEnoughReplays),
              hasAvailability = hasAvailability,
              selectedRace = None
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

  private def checkRegistrationRequirements(userId: Long, race: String): Future[TournamentRegistrationRequirements] = {
    import models.StarCraftModels.*
    val raceOpt = race match {
      case "Protoss" => Some(Protoss)
      case "Zerg"    => Some(Zerg)
      case "Terran"  => Some(Terran)
      case _         => None
    }

    raceOpt match {
      case None => Future.successful(TournamentRegistrationRequirements(false, false, None, None))
      case Some(scRace) =>
        for {
          replayCounts <- registrationValidationService.getReplayCountsPerMatchup(userId, scRace)
          hasAvail <- registrationValidationService.hasAvailabilityTimes(userId)
        } yield {
          val matchupCounts = MatchupReplayCounts(
            vsProtoss = replayCounts.getOrElse(Protoss, 0),
            vsZerg = replayCounts.getOrElse(Zerg, 0),
            vsTerran = replayCounts.getOrElse(Terran, 0)
          )
          TournamentRegistrationRequirements(
            hasEnoughReplays = matchupCounts.hasEnoughPerMatchup(2),
            hasAvailability = hasAvail,
            selectedRace = Some(race),
            replayCounts = Some(matchupCounts)
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
      race: String
  ): Future[Either[String, Unit]] = {
    tournamentService.registerUser(tournamentId, userId, tournamentCode, race).map(_.map(_ => ()))
  }

  def isUserAbleToRegister(
      userId: Long,
      race: String
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
