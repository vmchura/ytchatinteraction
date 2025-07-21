package services

import models.*
import models.dao.TournamentChallongeDAO

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TournamentMatchService @Inject()(
  tournamentService: TournamentService,
  tournamentChallongeService: TournamentChallongeService,
  tournamentChallongeDAO: TournamentChallongeDAO
)(implicit ec: ExecutionContext) {

  case class TournamentData(
    openTournaments: List[Tournament],
    tournamentsWithRegistrationStatus: Map[Tournament, Boolean],
    inProgressTournaments: List[Tournament]
  )

  def getTournamentData(userId: Long): Future[TournamentData] = {
    for {
      openTournaments <- tournamentService.getOpenTournaments
      userRegistrations <- Future.sequence(openTournaments.map { tournament =>
        tournamentService.isUserRegistered(tournament.id, userId).map(tournament -> _)
      })
      tournamentsWithRegistrationStatus = userRegistrations.toMap
      inProgressTournaments <- tournamentService.getTournamentsByStatus(TournamentStatus.InProgress)
    } yield TournamentData(openTournaments, tournamentsWithRegistrationStatus, inProgressTournaments)
  }

  def getUserMatches(userId: Long, tournaments: List[Tournament]): Future[List[UserMatchInfo]] = {
    val tournamentsWithChallongeIds = tournaments.filter(_.challongeTournamentId.isDefined)

    val matchFutures = tournamentsWithChallongeIds.map { tournament =>
      getUserMatchesForTournament(userId, tournament)
    }

    Future.sequence(matchFutures).map(_.flatten)
  }

  private def getUserMatchesForTournament(userId: Long, tournament: Tournament): Future[List[UserMatchInfo]] = {
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

  def registerUserForTournament(tournamentId: Long, userId: Long): Future[Either[String, Unit]] = {
    tournamentService.registerUser(tournamentId, userId).map(_.map(_ => ()))
  }

  def isUserRegisteredForTournament(tournamentId: Long, userId: Long): Future[Boolean] = {
    tournamentService.isUserRegistered(tournamentId, userId)
  }
}
