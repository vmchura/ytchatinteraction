package modules.gameparser

import models.ServerStarCraftModels._
import services.ParseReplayFileService
import models.StarCraftModels.*

import java.io.File
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import javax.inject.*

/**
 * GameParser service for parsing replay files without actors
 */
@Singleton
class GameParser @Inject()(
  parseReplayFileService: ParseReplayFileService
)(implicit ec: ExecutionContext) {
  /**
   * Parse replay data directly from Either result
   */
  def parseReplayData(data: Either[String, String]): GameInfo = {
    GameInfo.parseFromEither(data)
  }

  /**
   * Parse replay data from JSON string
   */
  def parseReplayJson(jsonStr: String): GameInfo = {
    Try(GameInfo.parseFromJson(jsonStr)).getOrElse(ImpossibleToParse)
  }
}
