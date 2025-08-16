package modules.gameparser

import services.ParseReplayFileService
import models.StarCraftModels._
import java.io.File
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import javax.inject._

/**
 * GameParser service for parsing replay files without actors
 */
@Singleton
class GameParser @Inject()(
  parseReplayFileService: ParseReplayFileService
)(implicit ec: ExecutionContext) {

  /**
   * Parse a replay file and return GameInfo
   */
  def parseReplay(replay: File): Future[GameInfo] = {
    parseReplayFileService.parseFile(replay).map { result =>
      GameInfo.parseFromEither(result)
    }.recover {
      case _ => ImpossibleToParse
    }
  }

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
