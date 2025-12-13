package models

import java.time.Instant

case class UserSmurf(
    id: Long,
    matchId: Option[Long],
    tournamentId: Option[Long],
    casualMatchId: Option[Long],
    userId: Long,
    smurf: String,
    createdAt: Instant = Instant.now()
)

trait GenericUserSmurf:
  def toUserSmurf: UserSmurf

case class TournamentUserSmurf(
    id: Long,
    matchId: Long,
    tournamentId: Long,
    userId: Long,
    smurf: String,
    createdAt: Instant = Instant.now()
) extends GenericUserSmurf:
  override def toUserSmurf: UserSmurf = UserSmurf(
    id,
    Some(matchId),
    Some(tournamentId),
    None,
    userId,
    smurf,
    createdAt
  )
case class CasualUserSmurf(
    id: Long,
    casualMatchId: Long,
    userId: Long,
    smurf: String,
    createdAt: Instant = Instant.now()
) extends GenericUserSmurf:
  override def toUserSmurf: UserSmurf =
    UserSmurf(id, None, None, Some(casualMatchId), userId, smurf, createdAt)

object UserSmurf:
  given Conversion[UserSmurf, TournamentUserSmurf] = {
    case UserSmurf(
    id,
    Some(matchId),
    Some(tournamentId),
    None,
    userId,
    smurf,
    createdAt
    ) =>
      TournamentUserSmurf(
        id,
        matchId,
        tournamentId,
        userId,
        smurf,
        createdAt
      )
    case _ => throw new IllegalStateException("can not convert to TournamentUserSmurf")
  }
  given Conversion[UserSmurf, CasualUserSmurf] = {
    case UserSmurf(
    id,
    None,
    None,
    Some(casualMatchId),
    userId,
    smurf,
    createdAt
    ) =>
      CasualUserSmurf(
        id,
        casualMatchId,
        userId,
        smurf,
        createdAt
      )
    case _ => throw new IllegalStateException("can not convert to Casual User Smurf")
  }

