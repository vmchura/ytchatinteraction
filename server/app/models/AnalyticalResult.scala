package models

import models.StarCraftModels.SCRace

import java.time.Instant

// Case class
case class AnalyticalResult(
                             id: Long = 0L,
                             userId: Long,
                             matchId: Long,
                             userRace: SCRace,
                             rivalRace: SCRace,
                             originalFileName: String,
                             analysisStartedAt: Instant,
                             analysisFinishedAt: Option[Instant],
                             algorithmVersion: Option[String],
                             result: Option[Boolean]
                           )

case class AnalyticalResultView(userAlias: String,
                                userRace: SCRace,
                                rivalRace: SCRace,
                                originalFileName: String,
                                analysisStartedAt: Instant,
                                analysisFinishedAt: Option[Instant],
                                algorithmVersion: Option[String],
                                result: Option[Boolean])