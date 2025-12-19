package models

import models.StarCraftModels.SCRace
import java.time.Instant

case class EloUser(
    userId: Long,
    userRace: SCRace,
    rivalRace: SCRace,
    elo: Int,
    updatedAt: Instant
)

case class EloUserLog(
    id: Long = 0L,
    userId: Long,
    userRace: SCRace,
    rivalUserId: Long,
    rivalRace: SCRace,
    eventAt: Instant,
    userInitialElo: Int,
    rivalInitialElo: Int,
    matchId: Option[Long],
    casualMatchId: Option[Long],
    userNewElo: Int
)
case class EloUserLogWithRivalName(
    id: Long,
    userId: Long,
    userRace: SCRace,
    rivalUserId: Long,
    rivalUserName: String,
    rivalRace: SCRace,
    eventAt: Instant,
    userInitialElo: Int,
    rivalInitialElo: Int,
    matchId: Option[Long],
    casualMatchId: Option[Long],
    userNewElo: Int
)
case class EloUserWithName(
    userId: Long,
    userName: String,
    userRace: SCRace,
    rivalRace: SCRace,
    elo: Int,
    updatedAt: Instant
)

case class EloUserRaceSummary(
    userId: Long,
    userName: String,
    userRace: SCRace,
    averageElo: Int,
    maxUpdatedAt: Instant
)
