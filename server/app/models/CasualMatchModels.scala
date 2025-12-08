package models
import java.time.Instant
import models.StarCraftModels._
case class CasualMatch(
    id: Long = 0L,
    userId: Long,
    rivalUserId: Long,
    createdAt: Instant,
    status: String
)

case class CasualMatchFile(
    id: Long = 0L,
    casualMatchId: Long,
    sha256Hash: String,
    originalName: String,
    relativeDirectoryPath: String,
    savedFileName: String,
    uploadedAt: Instant,
    slotPlayerId: Int,
    rivalSlotPlayerId: Int,
    userRace: SCRace,
    rivalRace: SCRace,
    gameFrames: Int
)
