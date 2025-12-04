package models

import models.StarCraftModels.SCRace

import java.time.Instant

case class AnalyticalFile(
                           id: Long = 0L,
                           userId: Long,
                           sha256Hash: String,
                           originalName: String,
                           relativeDirectoryPath: String,
                           savedFileName: String,
                           uploadedAt: Instant,
                           slotPlayerId: Int,
                           userRace: SCRace,
                           rivalRace: SCRace,
                           gameFrames: Int,
                           otherOtherId: Option[Long],
                           algorithmId: Option[String]
                         )
