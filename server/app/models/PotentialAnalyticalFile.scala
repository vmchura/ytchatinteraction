package models
import models.StarCraftModels._
import models.UploadedFile
case class PotentialAnalyticalFile(
    uploadedFile: UploadedFile,
    userPlayer: SCPlayer,
    matchId: Long,
    rivalPlayer: SCPlayer,
    frames: Int
)
