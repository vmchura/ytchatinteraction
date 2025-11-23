package services

import javax.inject.*
import models.StarCraftModels.*
import models.repository.*
import models.*
import play.api.{Configuration, Logger}
import play.api.libs.json.JsArray
import java.nio.file.Path

import scala.concurrent.{ExecutionContext, Future}

case class RecoverPlaysMatch(uploadedFiles: Seq[UploadedFile], userRequestSmurf: List[String], rivalSmurf: List[String])

case class GamePlayUser(gamePlay: JsArray, userRace: SCRace, rivalRace: SCRace)

trait AnalyticalReplayService(implicit ec: ExecutionContext):
  def recoverReplays(userID: Long, matchID: Long): Future[RecoverPlaysMatch]

  def filterGamePlay(liveReplay: ReplayParsed, userRequestSmurf: List[String], rivalSmurf: List[String]): Option[GamePlayUser]

  def recoverBaseGames(userID: Long, userRace: SCRace, rivalRace: SCRace): Future[Seq[AnalyticalFile]]

  def loadFileAndParse(storedPath: Path): Future[Option[ReplayParsed]]

  def getGamePlays(userID: Long, matchID: Long): Future[(Seq[Option[GamePlayUser]], Seq[GamePlayUser])] = {
    for {
      playsMatch <- recoverReplays(userID, matchID)
      replaysParsedMatch <- Future.sequence(playsMatch.uploadedFiles.map(uf => loadFileAndParse(Path.of(uf.relativeDirectoryPath))))
      userGamePlays = replaysParsedMatch.map {
        case Some(rp) => filterGamePlay(rp, playsMatch.userRequestSmurf, playsMatch.rivalSmurf)
        case None => None
      }
    } yield {
      (userGamePlays, Nil)
    }
  }

@Singleton
class AnalyticalReplayServiceImpl @Inject(configuration: Configuration,
                                          parseReplayService: ParseReplayFileService,
                                          fileStorageService: FileStorageService,
                                          uploadedFileRepository: UploadedFileRepository,
                                          userSmurfRepository: UserSmurfRepository,
                                          analyticalFileRepository: AnalyticalFileRepository
                                         )(
                                           implicit ec: ExecutionContext
                                         ) extends AnalyticalReplayService {
  private val logger = Logger(getClass)

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
      liveReplay.teams.flatMap(_.participants).find(p => rivalSmurf.contains(p.name))) match {
      case (Some(userPlayer), Some(rivalPlayer)) =>
        val filteredValue = liveReplay.commands.value.filter(v => (v \ "PlayerID").asOpt[Int].contains(userPlayer.id))
        Option.when(filteredValue.nonEmpty)(GamePlayUser(JsArray(filteredValue), userPlayer.race, rivalPlayer.race))
      case _ => None
    }
  }

  def recoverBaseGames(userID: Long, userRace: SCRace, rivalRace: SCRace): Future[Seq[AnalyticalFile]] = {
    analyticalFileRepository.findByUserRace(userId = userID, race = userRace).map(_.filter(_.rivalRace == rivalRace))
  }

  def loadFileAndParse(storedPath: Path): Future[Option[ReplayParsed]] = {
    parseReplayService.processSingleFile(storedPath)
  }

}