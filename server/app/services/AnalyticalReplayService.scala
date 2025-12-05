package services

import javax.inject.*
import models.StarCraftModels.*
import models.repository.*
import models.*
import play.api.libs.concurrent.Futures
import play.api.{Configuration, Logger}
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.WSClient
import play.api.libs.ws.JsonBodyWritables.*
import scala.concurrent.duration._

import java.nio.file.Path
import scala.concurrent.{ExecutionContext, Future}

case class RecoverPlaysMatch(
    uploadedFiles: Seq[UploadedFile],
    userRequestSmurf: List[String],
    rivalSmurf: List[String]
)

case class GamePlayUser(
    gamePlay: JsArray,
    userRace: SCRace,
    rivalRace: SCRace,
    frames: Int,
    originalFileName: String
) {
  override def toString: String =
    f"GamePlayUser(${gamePlay.value.length}, $userRace, $rivalRace, frames=$frames)"
}

case class GamePlayBaseUser(gamePlay: JsArray, frames: Int) {
  override def toString: String =
    f"GamePlayBaseUser(${gamePlay.value.length}, frames=$frames)"
}

trait AnalyticalReplayService(futures: Futures)(implicit ec: ExecutionContext):
  def recoverReplays(userID: Long, matchID: Long): Future[RecoverPlaysMatch]

  def filterGamePlay(
      liveReplay: ReplayParsed,
      userRequestSmurf: List[String],
      rivalSmurf: List[String],
      originalFileName: String
  ): Option[GamePlayUser]

  def filterGamePlay(
      liveReplay: ReplayParsed,
      inGamePlayerID: Int,
      frames: Int
  ): Option[GamePlayBaseUser]

  def recoverBaseGames(
      userID: Long,
      userRace: SCRace,
      rivalRace: SCRace
  ): Future[Seq[AnalyticalFile]]

  def loadFileAndParse(storedPath: Path): Future[Option[ReplayParsed]]

  def getGamePlays(
      userID: Long,
      matchID: Long
  ): Future[(Seq[GamePlayUser], Seq[GamePlayBaseUser])] = {
    for {
      playsMatch <- recoverReplays(userID, matchID)
      replaysParsedMatch <- Future.sequence(
        playsMatch.uploadedFiles.map(uf =>
          loadFileAndParse(Path.of(uf.relativeDirectoryPath)).map(rp =>
            (uf, rp)
          )
        )
      )
      userGamePlays = replaysParsedMatch.flatMap {
        case (uf, Some(rp)) =>
          filterGamePlay(
            rp,
            playsMatch.userRequestSmurf,
            playsMatch.rivalSmurf,
            uf.originalName
          )
        case _ => None
      }
      (userRace, rivalRace) <- userGamePlays
        .map(r => (r.userRace, r.rivalRace))
        .distinct match {
        case Seq((userRace, rivalRace)) =>
          Future.successful((userRace, rivalRace))
        case _ =>
          Future.failed(
            new IllegalStateException("Not all matches were the same race")
          )
      }
      baseGamesFiles <- recoverBaseGames(userID, userRace, rivalRace)
      baseGamesParsed <- Future.sequence(
        baseGamesFiles.map(af =>
          loadFileAndParse(Path.of(af.relativeDirectoryPath))
        )
      )
      gamePlayBase = baseGamesFiles.zip(baseGamesParsed).map {
        case (af, Some(rp)) =>
          filterGamePlay(rp, af.slotPlayerId, af.gameFrames)
        case (_, None) => None
      }
    } yield {
      (userGamePlays, gamePlayBase.flatten)
    }
  }

  def analyticalProcess(
      gamePlays: Seq[GamePlayBaseUser],
      gameTest: GamePlayUser
  ): Future[Option[(Boolean, String)]]

  def analyticalProcess(
      userID: Long,
      matchID: Long
  ): Future[Seq[AnalyticalResult]] = {
    for {
      gamePlays <- getGamePlays(userID, matchID)
      response <- Future.sequence(gamePlays._1.map(gp => {
        val defaultAnalyticalResponse = AnalyticalResult(
          0,
          userID,
          matchID,
          gp.userRace,
          gp.rivalRace,
          gp.originalFileName,
          analysisStartedAt = java.time.Instant.now(),
          analysisFinishedAt = None,
          algorithmVersion = None,
          result = None
        )
        (for {
          singularResponse <- futures.timeout(5.minutes) {
            analyticalProcess(gamePlays._2, gp)
          }
        } yield {
          singularResponse match {
            case Some((statisticalDifferent, version)) =>
              defaultAnalyticalResponse.copy(
                result = Some(statisticalDifferent),
                algorithmVersion = Some(version),
                analysisFinishedAt = Some(java.time.Instant.now())
              )
            case None =>
              defaultAnalyticalResponse.copy(analysisFinishedAt =
                Some(java.time.Instant.now())
              )
          }
        }).recover(_ => defaultAnalyticalResponse)
      }))
    } yield {
      response
    }
  }

  def analyticalProcessMatch(
      tournamentId: Long,
      challongeMatchID: Long
  ): Future[Boolean]

@Singleton
class AnalyticalReplayServiceImpl @Inject (
    wsClient: WSClient,
    configuration: Configuration,
    parseReplayService: ParseReplayFileService,
    fileStorageService: FileStorageService,
    uploadedFileRepository: UploadedFileRepository,
    userSmurfRepository: UserSmurfRepository,
    analyticalFileRepository: AnalyticalFileRepository,
    tournamentService: TournamentService,
    analyticalResultRepository: AnalyticalResultRepository,
    futures: Futures,
    eloRepository: EloRepository
)(implicit
    ec: ExecutionContext
) extends AnalyticalReplayService(futures) {
  private val logger = Logger(getClass)
  private val replayAnalyticalUrl =
    configuration.get[String]("replayanalytical.url")

  def recoverReplays(userID: Long, matchID: Long): Future[RecoverPlaysMatch] = {

    for {
      uploadedFiles <- uploadedFileRepository.findByMatchId(matchID)
      smurfsMatch <- userSmurfRepository.findByMatchId(matchID)
    } yield {
      RecoverPlaysMatch(
        uploadedFiles,
        userRequestSmurf = smurfsMatch.filter(_.userId == userID).map(_.smurf),
        rivalSmurf = smurfsMatch.filter(_.userId != userID).map(_.smurf)
      )
    }
  }

  def filterGamePlay(
      liveReplay: ReplayParsed,
      userRequestSmurf: List[String],
      rivalSmurf: List[String],
      originalFileName: String
  ): Option[GamePlayUser] = {
    (
      liveReplay.teams
        .flatMap(_.participants)
        .find(p => userRequestSmurf.contains(p.name)),
      liveReplay.teams
        .flatMap(_.participants)
        .find(p => rivalSmurf.contains(p.name)),
      liveReplay.frames
    ) match {
      case (Some(userPlayer), Some(rivalPlayer), Some(frames)) =>
        val filteredValue = liveReplay.commands.value.filter(v =>
          (v \ "PlayerID").asOpt[Int].contains(userPlayer.id)
        )
        Option.when(filteredValue.nonEmpty)(
          GamePlayUser(
            JsArray(filteredValue),
            userPlayer.race,
            rivalPlayer.race,
            frames,
            originalFileName
          )
        )
      case _ => None
    }
  }

  def filterGamePlay(
      liveReplay: ReplayParsed,
      inGamePlayerID: Int,
      frames: Int
  ): Option[GamePlayBaseUser] = {
    val filteredValue = liveReplay.commands.value.filter(v =>
      (v \ "PlayerID").asOpt[Int].contains(inGamePlayerID)
    )
    Option.when(filteredValue.nonEmpty)(
      GamePlayBaseUser(JsArray(filteredValue), frames)
    )
  }

  def recoverBaseGames(
      userID: Long,
      userRace: SCRace,
      rivalRace: SCRace
  ): Future[Seq[AnalyticalFile]] = {
    analyticalFileRepository
      .findByUserRace(userId = userID, race = userRace)
      .map(_.filter(_.rivalRace == rivalRace))
  }

  def loadFileAndParse(storedPath: Path): Future[Option[ReplayParsed]] = {
    parseReplayService.processSingleFile(storedPath)
  }

  def analyticalProcess(
      gamePlays: Seq[GamePlayBaseUser],
      gameTest: GamePlayUser
  ): Future[Option[(Boolean, String)]] = {
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
          for {
            isDifferent <- (response.json \ "is_different").asOpt[Boolean]
            algorithmVersion <- (response.json \ "algorithm_version")
              .asOpt[String]
          } yield {
            (isDifferent, algorithmVersion)
          }

        } else {
          logger.warn(
            s"Analysis service returned status ${response.status}: ${response.body}"
          )
          None
        }
      }
      .recover { case ex: Exception =>
        None
      }
  }

  def analyticalProcessMatch(
      tournamentId: Long,
      challongeMatchID: Long
  ): Future[Boolean] = {
    for {
      singleMatchOption <- tournamentService.getMatch(
        tournamentId,
        challongeMatchID
      )
      singleMatch <- singleMatchOption.fold(
        Future.failed(new IllegalStateException("No match registered"))
      )(tm => Future.successful(tm))
      resultFirstUser <- analyticalProcess(
        singleMatch.firstUserId,
        challongeMatchID
      ).recover(_ => Nil)
      resultSecondUser <- analyticalProcess(
        singleMatch.secondUserId,
        challongeMatchID
      ).recover(_ => Nil)
      firstUserRace = resultFirstUser.map(_.userRace).toSet
      secondUserRace = resultSecondUser.map(_.userRace).toSet
      _ = Option.when((firstUserRace.size == 1) && (secondUserRace.size == 1)) {
        eloRepository.apply_first_user_win(
          singleMatch.firstUserId,
          firstUserRace.head,
          singleMatch.secondUserId,
          secondUserRace.head,
          Some(singleMatch.matchId),
          None
        )
      }
      registrationResults <- Future.sequence(
        (resultFirstUser ++ resultSecondUser).map(ar =>
          analyticalResultRepository.create(ar)
        )
      )
    } yield {
      registrationResults.nonEmpty
    }
  }
}
