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
    ytUserRepository: YtUserRepository
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

    } yield TournamentViewDataForUser(
      registered.map(t =>
        TournamentOpenDataUser(
          t.id,
          t.name,
          TournamentRegistrationUserStatus.Registered,
          None
        )
      ) ++
        nonRegistered.map { t =>
          TournamentOpenDataUser(
            t.id,
            t.name,
            TournamentRegistrationUserStatus.Unregistered,
            None
          )
        },
      inProgressTournaments.flatMap { t =>
        t.challongeUrl.zip(t.challongeTournamentId).map { case (url, id) =>
          InProgressTournament(t.id, t.name, id, url)
        }
      }
    )
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
      tournamentCode: Option[String]
  ): Future[Either[String, Unit]] = {
    tournamentService.registerUser(tournamentId, userId, tournamentCode).map(_.map(_ => ()))
  }

  def isUserRegisteredForTournament(
      tournamentId: Long,
      userId: Long
  ): Future[Boolean] = {
    tournamentService.isUserRegistered(tournamentId, userId)
  }

}
