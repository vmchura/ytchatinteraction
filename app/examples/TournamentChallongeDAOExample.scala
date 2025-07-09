package examples

import models.dao.TournamentChallongeDAO
import models.{Tournament, User}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Example showing how to use the TournamentChallongeDAO
 * This is for documentation/demonstration purposes
 */
@Singleton
class TournamentChallongeDAOExample @Inject()(
  tournamentChallongeDAO: TournamentChallongeDAO
)(implicit ec: ExecutionContext) {

  /**
   * Example: Create a mapping after adding a participant to Challonge
   */
  def exampleCreateMapping(tournamentId: Long, userId: Long, challongeParticipantId: Long, challongeTournamentId: Long): Future[Unit] = {
    for {
      mapping <- tournamentChallongeDAO.createChallongeParticipantMapping(
        tournamentId,
        userId,
        challongeParticipantId,
        challongeTournamentId
      )
      _ = println(s"Created mapping: User $userId -> Challonge Participant ${mapping.challongeParticipantId}")
    } yield ()
  }

  /**
   * Example: Get Challonge participant ID for a user in a tournament
   */
  def exampleGetParticipantId(tournamentId: Long, userId: Long): Future[Option[Long]] = {
    tournamentChallongeDAO.getChallongeParticipantId(tournamentId, userId).map { participantIdOpt =>
      participantIdOpt match {
        case Some(participantId) =>
          println(s"User $userId has Challonge participant ID: $participantId")
        case None =>
          println(s"User $userId does not have a Challonge participant ID for tournament $tournamentId")
      }
      participantIdOpt
    }
  }

  /**
   * Example: Get all participants with their Challonge IDs for a tournament
   */
  def exampleGetTournamentParticipants(tournamentId: Long): Future[List[(User, Option[Long])]] = {
    tournamentChallongeDAO.getTournamentParticipantsWithChallongeIds(tournamentId).map { participants =>
      println(s"Tournament $tournamentId participants:")
      participants.foreach { case (user, challongeIdOpt) =>
        challongeIdOpt match {
          case Some(challongeId) =>
            println(s"  - ${user.userName} (ID: ${user.userId}) -> Challonge ID: $challongeId")
          case None =>
            println(s"  - ${user.userName} (ID: ${user.userId}) -> No Challonge ID")
        }
      }
      participants
    }
  }

  /**
   * Example: Find tournament and user by Challonge participant ID
   */
  def exampleFindByChallongeParticipantId(challongeParticipantId: Long): Future[Option[(Tournament, User)]] = {
    tournamentChallongeDAO.getTournamentUserByChallongeParticipantId(challongeParticipantId).map { result =>
      result match {
        case Some((tournament, user)) =>
          println(s"Challonge participant $challongeParticipantId belongs to:")
          println(s"  Tournament: ${tournament.name} (ID: ${tournament.id})")
          println(s"  User: ${user.userName} (ID: ${user.userId})")
        case None =>
          println(s"No tournament/user found for Challonge participant ID: $challongeParticipantId")
      }
      result
    }
  }

  /**
   * Example: Check if a user has been assigned a Challonge participant ID
   */
  def exampleCheckHasChallongeId(tournamentId: Long, userId: Long): Future[Boolean] = {
    tournamentChallongeDAO.hasChallongeParticipantId(tournamentId, userId).map { hasId =>
      if (hasId) {
        println(s"User $userId has a Challonge participant ID for tournament $tournamentId")
      } else {
        println(s"User $userId does NOT have a Challonge participant ID for tournament $tournamentId")
      }
      hasId
    }
  }

  /**
   * Example: Update Challonge participant ID for a user (if needed)
   */
  def exampleUpdateParticipantId(tournamentId: Long, userId: Long, newChallongeParticipantId: Long): Future[Boolean] = {
    tournamentChallongeDAO.updateChallongeParticipantId(tournamentId, userId, newChallongeParticipantId).map {
      case Some(updated) =>
        println(s"Updated Challonge participant ID for user $userId to: ${updated.challongeParticipantId}")
        true
      case None =>
        println(s"Could not update Challonge participant ID for user $userId (mapping not found)")
        false
    }
  }

  /**
   * Example: Remove Challonge participant mapping (when user withdraws)
   */
  def exampleRemoveMapping(tournamentId: Long, userId: Long): Future[Boolean] = {
    tournamentChallongeDAO.removeChallongeParticipantMapping(tournamentId, userId).map { removed =>
      if (removed) {
        println(s"Removed Challonge participant mapping for user $userId from tournament $tournamentId")
      } else {
        println(s"No Challonge participant mapping found for user $userId in tournament $tournamentId")
      }
      removed
    }
  }

  /**
   * Example: Remove all Challonge participant mappings for a tournament (when tournament is deleted)
   */
  def exampleRemoveAllMappings(tournamentId: Long): Future[Int] = {
    tournamentChallongeDAO.removeAllChallongeParticipantMappings(tournamentId).map { count =>
      println(s"Removed $count Challonge participant mappings for tournament $tournamentId")
      count
    }
  }
}
