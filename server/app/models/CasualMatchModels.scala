package models
import evolutioncomplete.WinnerShared
import evolutioncomplete.WinnerShared._

import java.time.Instant
import models.StarCraftModels.*
case class CasualMatch(
    id: Long = 0L,
    userId: Long,
    rivalUserId: Long,
    winnerUserId: Option[Long],
    createdAt: Instant,
    status: MatchStatus
):
  def withWinner(winnerShared: WinnerShared): CasualMatch =
    winnerShared match {
      case Undefined | Draw | Cancelled => copy(winnerUserId = None)
      case FirstUser | FirstUserByOnlyPresented => copy(winnerUserId = Some(userId))
      case SecondUser | SecondUserByOnlyPresented => copy(winnerUserId = Some(rivalUserId))
    }


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
) extends GenericUploadedFile
