package services

import models.*
import models.dao.TournamentChallongeDAO
import models.repository.{ContentCreatorChannelRepository, YtUserRepository}
import models.viewmodels.*
import models.viewmodels.TournamentRegistrationUserStatus.*
import services.YoutubeMembershipService

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TournamentMatchService @Inject()(
  tournamentService: TournamentService,
  tournamentChallongeService: TournamentChallongeService,
  tournamentChallongeDAO: TournamentChallongeDAO,
  youtubeMembershipService: YoutubeMembershipService,
  contentCreatorChannelRepository: ContentCreatorChannelRepository,
  ytUserRepository: YtUserRepository
)(implicit ec: ExecutionContext) {



  def getTournamentData(userId: Long): Future[TournamentViewDataForUser] = {
    for {
      userYtChannelOption <- ytUserRepository.getByUserId(userId).map(_.headOption)
      userYtChannel <- userYtChannelOption.fold(throw new IllegalStateException())(Future.successful)
      openTournaments <- tournamentService.getOpenTournaments
      (registeredTuple, nonRegisteredTuple) <- Future.sequence(openTournaments.map { tournament =>
        tournamentService.isUserRegistered(tournament.id, userId).map(tournament -> _)
      }).map(_.partition(_._2))
      registered = registeredTuple.map(_._1)
      nonRegistered = nonRegisteredTuple.map(_._1)
      (publicTournamentNonRegistered, subscribersOnlyTournamentNonRegistered) = nonRegistered.partition(_.contentCreatorChannelId.isEmpty)
      channelContentCreatorNotRegistered <- Future.sequence(subscribersOnlyTournamentNonRegistered.flatMap(_.contentCreatorChannelId).distinct.map(contentCreatorChannelRepository.findById)).map(_.flatten)
      contentCreatorWithTournamentNotRegistered = subscribersOnlyTournamentNonRegistered.flatMap(t => channelContentCreatorNotRegistered.find(_.id.exists(t.contentCreatorChannelId.contains)).map(cc => (cc, t)))
      (subscribedTupleNotRegistered, notSubscribedTupleNotRegistered) <- Future.sequence(contentCreatorWithTournamentNotRegistered.map{ (cc,t) => youtubeMembershipService.isSubscribedToChannel(
        userYtChannel.userChannelId,
        cc.youtubeChannelId
      ).map(subscriber => ((t, cc), subscriber))}).map(_.partition(_._2))
      subscribedNotRegistered = subscribedTupleNotRegistered.map(_._1)
      notSubscribedNotRegistered = notSubscribedTupleNotRegistered.map(_._1)
      inProgressTournaments <- tournamentService.getTournamentsByStatus(TournamentStatus.InProgress)

    } yield TournamentViewDataForUser(
      registered.map(t => TournamentOpenDataUser(t.id, t.name, TournamentRegistrationUserStatus.Registered, None)) ++
        subscribedNotRegistered.map{case (t, cc) => TournamentOpenDataUser(t.id, t.name, Unregistered, Some(cc) )} ++
        notSubscribedNotRegistered.map{case (t, cc) => TournamentOpenDataUser(t.id, t.name, NotAbleToRegister, Some(cc) )} ++
        publicTournamentNonRegistered.map{t => TournamentOpenDataUser(t.id, t.name, Unregistered, None )},
      inProgressTournaments.flatMap{ t => t.challongeUrl.zip(t.challongeTournamentId).map{case (url, id) => InProgressTournament(t.id, t.name, id, url)}}
    )
  }

  def getUserMatches(userId: Long, tournaments: List[InProgressTournament]): Future[List[UserMatchInfo]] = {

    val matchFutures = tournaments.map { tournament =>
      getUserMatchesForTournament(userId, tournament)
    }

    Future.sequence(matchFutures).map(_.flatten)
  }

  private def getUserMatchesForTournament(userId: Long, tournament: InProgressTournament): Future[List[UserMatchInfo]] = {

    for {
      participantOpt <- tournamentChallongeDAO.getTournamentChallongeParticipants(tournament.id)
        .map(_.find(_.userId == userId))
      matches <- participantOpt match {
        case Some(participant) =>
          tournamentChallongeService.getMatchesForParticipant(tournament.challongeID, participant.challongeParticipantId)
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

  private def checkSubscriptionStatus(tournaments: List[Tournament], userYtChannel: Option[YtUser]): Future[Map[Tournament, Boolean]] = {
    userYtChannel match {
      case None =>
        // User doesn't have YouTube channel, so no subscriptions
        Future.successful(tournaments.map(_ -> false).toMap)
      case Some(ytUser) =>
        val subscriptionChecks = tournaments.map { tournament =>
          tournament.contentCreatorChannelId match {
            case None =>
              // Tournament has no content creator requirement, everyone can register
              Future.successful(tournament -> true)
            case Some(contentCreatorChannelId) =>
              // Check if user is subscribed to the content creator
              contentCreatorChannelRepository.findById(contentCreatorChannelId).flatMap {
                case None => Future.successful(tournament -> false)
                case Some(contentCreatorChannel) =>
                  youtubeMembershipService.isSubscribedToChannel(
                    ytUser.userChannelId,
                    contentCreatorChannel.youtubeChannelId
                  ).map(tournament -> _)
              }
          }
        }
        Future.sequence(subscriptionChecks).map(_.toMap)
    }
  }
}
