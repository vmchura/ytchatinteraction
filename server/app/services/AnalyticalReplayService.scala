package services

import javax.inject.*
import models.StarCraftModels.*
import models.repository.*
import models.*
import models.ServerStarCraftModels.ReplayParsed
import play.api.libs.concurrent.Futures
import play.api.{Configuration, Logger}
import play.api.libs.json.{JsArray, Json}
import play.api.libs.ws.WSClient
import play.api.libs.ws.JsonBodyWritables.*

import scala.concurrent.duration.*
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
trait AnalyticalContext[AC]:
  def getContext: Future[Option[AC]]
  def getPlaysMatchFirstUser(ac: AC): Future[RecoverPlaysMatch]
  def getPlaysMatchSecondUser(ac: AC): Future[RecoverPlaysMatch]
  def withGamePlay(userID: Long, gp: GamePlayUser): GenericAnalyticalResult
  def getFirstUserID(ac: AC): Long
  def getSecondUserID(ac: AC): Long
  def getWinnerID(ac: AC): Option[Long]
  def getMatchID(ac: AC): Option[Long]
  def getCasualMatchID(ac: AC): Option[Long]


trait AnalyticalReplayService(futures: Futures)(implicit ec: ExecutionContext):
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
      playsMatch: RecoverPlaysMatch
  ): Future[(Seq[GamePlayUser], Seq[GamePlayBaseUser])] = {
    for {
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

//  def getTournamentGamePlays(userID: Long, matchID: Long): Future[(Seq[GamePlayUser], Seq[GamePlayBaseUser])] = for {
//    playsMatch <- recoverTournamentReplays(userID, matchID)
//    gamePlays <- getGames(playsMatch)
//  } yield gamePlays
//
//  def getCasualMatchGamePlays(userID: Long, casualMatchId: Long): Future[(Seq[GamePlayUser], Seq[GamePlayBaseUser])] = for {
//    playsMatch <- recoverCasualMatchReplays(userID, casualMatchId)
//    gamePlays <- getGames(playsMatch)}
//    yield gamePlays

  def analyticalProcessByAlgorithm(
      gamePlays: Seq[GamePlayBaseUser],
      gameTest: GamePlayUser
  ): Future[Option[(Boolean, String)]]

  def analyticalProcessMatch[AC](analyticalContext: AnalyticalContext[AC]): Future[Boolean]

  def analyticalProcess[AC](
      ac: AnalyticalContext[AC],
      userID: Long,
      gamePlays: (Seq[GamePlayUser], Seq[GamePlayBaseUser])
  ): Future[Seq[GenericAnalyticalResult]] = {
    for {
      response <- Future.sequence(gamePlays._1.map(gp => {
        val defaultAnalyticalResponse = ac.withGamePlay(userID, gp)
        (for {
          singularResponse <- futures.timeout(5.minutes) {
            analyticalProcessByAlgorithm(gamePlays._2, gp)
          }
        } yield {
          singularResponse match {
            case Some((statisticalDifferent, version)) =>
              defaultAnalyticalResponse.withResults(
                result = statisticalDifferent,
                algorithmVersion = version,
                analysisFinishedAt = java.time.Instant.now()
              )
            case None =>
              defaultAnalyticalResponse.withAnalysisFinishedAt(analysisFinishedAt =
                java.time.Instant.now()
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

  def analyticalProcessCasualMatch(casualMatchId: Long): Future[Boolean]

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
    eloRepository: EloRepository,
    casualMatchRepository: CasualMatchRepository
)(implicit
    ec: ExecutionContext
) extends AnalyticalReplayService(futures) {
  private val logger = Logger(getClass)
  private val replayAnalyticalUrl =
    configuration.get[String]("replayanalytical.url")

  case class TournamentAnalyticalContext(tournamentID: Long, matchID: Long) extends AnalyticalContext[TournamentMatch]:
    def getContext: Future[Option[TournamentMatch]] = tournamentService.getMatch(tournamentID, matchID)
    private def recoverReplays(userID: Long, matchID: Long): Future[RecoverPlaysMatch] = {

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
    override def getPlaysMatchFirstUser(ac: TournamentMatch): Future[RecoverPlaysMatch] = recoverReplays(ac.firstUserId, ac.matchId)
    override def getPlaysMatchSecondUser(ac: TournamentMatch): Future[RecoverPlaysMatch] = recoverReplays(ac.secondUserId, ac.matchId)

    override def withGamePlay(userID: Long, gp: GamePlayUser): GenericAnalyticalResult = TournamentAnalyticalResult(
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
    override def getCasualMatchID(ac: models.TournamentMatch): Option[Long] = None
    override def getFirstUserID(ac: models.TournamentMatch): Long = ac.firstUserId
    override def getMatchID(ac: models.TournamentMatch): Option[Long] = Some(matchID)
    override def getSecondUserID(ac: models.TournamentMatch): Long = ac.secondUserId
    override def getWinnerID(ac: models.TournamentMatch): Option[Long] = ac.winnerUserId


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

  def analyticalProcessByAlgorithm(
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

  def analyticalProcessMatch[AC](
      analyticalContext: AnalyticalContext[AC]
  ): Future[Boolean] = {
    for {
      singleMatchOption <- analyticalContext.getContext
      singleMatch <- singleMatchOption.fold(Future.failed(new IllegalStateException("No match registered")))(ac => Future.successful(ac))

      firstUserID = analyticalContext.getFirstUserID(singleMatch)
      gamePlaysFirstUser <- analyticalContext.getPlaysMatchFirstUser(singleMatch)
      gamesFirstUser <- getGamePlays(analyticalContext.getFirstUserID(singleMatch), gamePlaysFirstUser)
      resultFirstUser <- analyticalProcess(analyticalContext, firstUserID, gamesFirstUser).recover(_ => Nil)
      firstUserRace = resultFirstUser.map(_.userRace).toSet

      secondUserID = analyticalContext.getSecondUserID(singleMatch)
      gamePlaysSecondUser <- analyticalContext.getPlaysMatchSecondUser(singleMatch)
      gamesSecondUser <- getGamePlays(analyticalContext.getSecondUserID(singleMatch), gamePlaysSecondUser)
      resultSecondUser <- analyticalProcess(analyticalContext, secondUserID, gamesSecondUser).recover(_ => Nil)
      secondUserRace = resultSecondUser.map(_.userRace).toSet

      _ = Option.when((firstUserRace.size == 1) && (secondUserRace.size == 1)) {
        if(analyticalContext.getWinnerID(singleMatch).contains(analyticalContext.getFirstUserID(singleMatch))){
          eloRepository.apply_first_user_win(
            analyticalContext.getFirstUserID(singleMatch),
            firstUserRace.head,
            analyticalContext.getSecondUserID(singleMatch),
            secondUserRace.head,
            analyticalContext.getMatchID(singleMatch),
            analyticalContext.getCasualMatchID(singleMatch)
          )
        }else {
          if (analyticalContext.getWinnerID(singleMatch).contains(analyticalContext.getSecondUserID(singleMatch))) {
            eloRepository.apply_first_user_win(
              analyticalContext.getSecondUserID(singleMatch),
              secondUserRace.head,
              analyticalContext.getFirstUserID(singleMatch),
              firstUserRace.head,
              analyticalContext.getMatchID(singleMatch),
              analyticalContext.getCasualMatchID(singleMatch)
            )
          }
        }
      }
      registrationResults <- Future.sequence(
        (resultFirstUser ++ resultSecondUser).map(ar =>
          analyticalResultRepository.create(ar.toAnalyticalResult)
        )
      )
    } yield {
      registrationResults.nonEmpty
    }
  }

  override def analyticalProcessCasualMatch(casualMatchId: Long): Future[Boolean] = ???

  override def analyticalProcessMatch(tournamentId: Long, challongeMatchID: Long): Future[Boolean] = analyticalProcessMatch(TournamentAnalyticalContext(tournamentId, challongeMatchID))

}

