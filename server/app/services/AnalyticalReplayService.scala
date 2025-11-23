package services

import javax.inject.*
import models.StarCraftModels.*
import models.repository.*
import models.*
import play.api.{Configuration, Logger}
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.WSClient
import play.api.libs.ws.JsonBodyWritables.*

import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future}

case class RecoverPlaysMatch(uploadedFiles: Seq[UploadedFile], userRequestSmurf: List[String], rivalSmurf: List[String])

trait GamePlay:
  def gamePlay: JsArray

  def frames: Int

case class GamePlayUser(gamePlay: JsArray, userRace: SCRace, rivalRace: SCRace, frames: Int) extends GamePlay {
  override def toString: String = f"GamePlayUser(${gamePlay.value.length}, $userRace, $rivalRace, frames=$frames)"
}

case class GamePlayBaseUser(gamePlay: JsArray, frames: Int) extends GamePlay {
  override def toString: String = f"GamePlayBaseUser(${gamePlay.value.length}, frames=$frames)"
}

trait AnalyticalReplayService(implicit ec: ExecutionContext):
  def recoverReplays(userID: Long, matchID: Long): Future[RecoverPlaysMatch]

  def filterGamePlay(liveReplay: ReplayParsed, userRequestSmurf: List[String], rivalSmurf: List[String]): Option[GamePlayUser]

  def filterGamePlay(liveReplay: ReplayParsed, inGamePlayerID: Int, frames: Int): Option[GamePlayBaseUser]

  def recoverBaseGames(userID: Long, userRace: SCRace, rivalRace: SCRace): Future[Seq[AnalyticalFile]]

  def loadFileAndParse(storedPath: Path): Future[Option[ReplayParsed]]

  def getGamePlays(userID: Long, matchID: Long): Future[(Seq[Option[GamePlay]], Seq[Option[GamePlay]])] = {
    for {
      playsMatch <- recoverReplays(userID, matchID)
      replaysParsedMatch <- Future.sequence(playsMatch.uploadedFiles.map(uf => loadFileAndParse(Path.of(uf.relativeDirectoryPath))))
      userGamePlays = replaysParsedMatch.map {
        case Some(rp) => filterGamePlay(rp, playsMatch.userRequestSmurf, playsMatch.rivalSmurf)
        case None => None
      }
      (userRace, rivalRace) <- userGamePlays.flatMap(_.map(r => (r.userRace, r.rivalRace))).distinct match {
        case Seq((userRace, rivalRace)) => Future.successful((userRace, rivalRace))
        case _ => Future.failed(new IllegalStateException("Not all matches were the same race"))
      }
      baseGamesFiles <- recoverBaseGames(userID, userRace, rivalRace)
      baseGamesParsed <- Future.sequence(baseGamesFiles.map(af => loadFileAndParse(Path.of(af.relativeDirectoryPath))))
      gamePlayBase = baseGamesFiles.zip(baseGamesParsed).map {
        case (af, Some(rp)) => filterGamePlay(rp, af.slotPlayerId, af.gameFrames)
        case (_, None) => None
      }
    } yield {
      (userGamePlays, gamePlayBase)
    }
  }

  def analyticalProcess(gamePlays: Seq[GamePlay], gameTest: GamePlay): Future[Option[Boolean]]

  def analyticalProcess(userID: Long, matchID: Long): Future[Seq[Option[Boolean]]] = {
    for {
      gamePlays <- getGamePlays(userID, matchID)
      validReferences = gamePlays._2.flatten
      response <- Future.sequence(gamePlays._1.map(test => test.fold(Future.successful(None))(gp => analyticalProcess(validReferences, gp))))
    } yield {
      response
    }
  }

@Singleton
class AnalyticalReplayServiceImpl @Inject(wsClient: WSClient,
                                          configuration: Configuration,
                                          parseReplayService: ParseReplayFileService,
                                          fileStorageService: FileStorageService,
                                          uploadedFileRepository: UploadedFileRepository,
                                          userSmurfRepository: UserSmurfRepository,
                                          analyticalFileRepository: AnalyticalFileRepository
                                         )(
                                           implicit ec: ExecutionContext
                                         ) extends AnalyticalReplayService {
  private val logger = Logger(getClass)
  private val replayAnalyticalUrl = configuration.get[String]("replayanalytical.url")

  def recoverReplays(userID: Long, matchID: Long): Future[RecoverPlaysMatch] = {

    for {
      uploadedFiles <- uploadedFileRepository.findByMatchId(matchID)
      smurfsMatch <- userSmurfRepository.findByMatchId(matchID)
    } yield {
      RecoverPlaysMatch(uploadedFiles,
        userRequestSmurf = smurfsMatch.filter(_.userId == userID).map(_.smurf),
        rivalSmurf = smurfsMatch.filter(_.userId != userID).map(_.smurf))
    }
  }

  def filterGamePlay(liveReplay: ReplayParsed, userRequestSmurf: List[String], rivalSmurf: List[String]): Option[GamePlayUser] = {
    (liveReplay.teams.flatMap(_.participants).find(p => userRequestSmurf.contains(p.name)),
      liveReplay.teams.flatMap(_.participants).find(p => rivalSmurf.contains(p.name)),
      liveReplay.frames) match {
      case (Some(userPlayer), Some(rivalPlayer), Some(frames)) =>
        val filteredValue = liveReplay.commands.value.filter(v => (v \ "PlayerID").asOpt[Int].contains(userPlayer.id))
        Option.when(filteredValue.nonEmpty)(GamePlayUser(JsArray(filteredValue), userPlayer.race, rivalPlayer.race, frames))
      case _ => None
    }
  }

  def filterGamePlay(liveReplay: ReplayParsed, inGamePlayerID: Int, frames: Int): Option[GamePlayBaseUser] = {
    val filteredValue = liveReplay.commands.value.filter(v => (v \ "PlayerID").asOpt[Int].contains(inGamePlayerID))
    Option.when(filteredValue.nonEmpty)(GamePlayBaseUser(JsArray(filteredValue), frames))
  }

  def recoverBaseGames(userID: Long, userRace: SCRace, rivalRace: SCRace): Future[Seq[AnalyticalFile]] = {
    analyticalFileRepository.findByUserRace(userId = userID, race = userRace).map(_.filter(_.rivalRace == rivalRace))
  }

  def loadFileAndParse(storedPath: Path): Future[Option[ReplayParsed]] = {
    parseReplayService.processSingleFile(storedPath)
  }

  def analyticalProcess(gamePlays: Seq[GamePlay], gameTest: GamePlay): Future[Option[Boolean]] = {
    val requestPayload = Json.obj(
      "game_plays" -> JsArray(gamePlays.map(_.gamePlay)),
      "game_test" -> gameTest.gamePlay
    )
    wsClient
      .url(s"$replayAnalyticalUrl/analyze")
      .withHttpHeaders("Content-Type" -> "application/json")
      .post(requestPayload)
      .map { response =>
        logger.debug(s"Response analytical")
        logger.debug(response.toString)
        if (response.status == 200) {
          (response.json \ "is_different").asOpt[Boolean]
        } else {
          logger.warn(s"Analysis service returned status ${response.status}: ${response.body}")
          None
        }
      }.recover {
        case ex: Exception => None
      }
  }
}