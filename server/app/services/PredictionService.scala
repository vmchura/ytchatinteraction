package services

import models.{
  PredictionBet,
  PredictionBetType,
  PredictionEvent,
  PredictionEventStatus,
  PredictionMatchup,
  PredictionMatchupStatus,
  PredictionUserScore,
  PredictionUserScoreWithUsername
}
import models.repository.{
  PredictionBetRepository,
  PredictionEventRepository,
  PredictionMatchupRepository,
  PredictionUserScoreRepository
}
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{JsValue, Json}
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PredictionService @Inject()(
  eventRepository: PredictionEventRepository,
  matchupRepository: PredictionMatchupRepository,
  betRepository: PredictionBetRepository,
  scoreRepository: PredictionUserScoreRepository,
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) {

  private val logger = Logger(getClass)
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  private val db = dbConfig.db
  import dbConfig.profile.api.*

  def createEvent(name: String): Future[PredictionEvent] = {
    eventRepository.create(PredictionEvent(name = name))
  }

  def getEvent(id: Long): Future[Option[PredictionEvent]] = {
    eventRepository.findById(id)
  }

  def getAllEvents: Future[Seq[PredictionEvent]] = {
    eventRepository.findAll()
  }

  def createMatchup(matchup: PredictionMatchup): Future[PredictionMatchup] = {
    matchupRepository.create(matchup)
  }

  def getMatchup(id: Long): Future[Option[PredictionMatchup]] = {
    matchupRepository.findById(id)
  }

  def getMatchupsByEvent(eventId: Long): Future[Seq[PredictionMatchup]] = {
    matchupRepository.findByEventId(eventId)
  }

  def getMatchupsByEventAndRound(eventId: Long, round: Int): Future[Seq[PredictionMatchup]] = {
    matchupRepository.findByEventIdAndRound(eventId, round)
  }

  def updateMatchup(matchup: PredictionMatchup): Future[Option[PredictionMatchup]] = {
    matchupRepository.update(matchup)
  }

  def lockMatchup(id: Long): Future[Option[PredictionMatchup]] = {
    matchupRepository.updateStatus(id, PredictionMatchupStatus.Locked)
  }

  def placeBet(userId: Long, matchupId: Long, selection: String, pointsBet: Int): Future[Either[String, PredictionBet]] = {
    for {
      matchupOpt <- matchupRepository.findById(matchupId)
      result <- matchupOpt match {
        case None =>
          Future.successful(Left("Matchup not found"))
        case Some(matchup) if matchup.status != PredictionMatchupStatus.Open =>
          Future.successful(Left("Matchup is not open for betting"))
        case Some(matchup) if !matchup.multipliers.contains(selection) =>
          Future.successful(Left(s"Invalid selection: $selection"))
        case Some(matchup) if pointsBet < 1 || pointsBet > 100 =>
          Future.successful(Left("Bet must be between 1 and 100 points"))
        case Some(matchup) =>
          for {
            currentTotal <- betRepository.findTotalBetByUserForMatchup(userId, matchupId)
            existingBets <- betRepository.findByUserAndMatchup(userId, matchupId)
            result <- {
              val alreadyUsed = currentTotal
              val selectionExists = existingBets.exists(_.selection == selection)
              if (selectionExists) {
                Future.successful(Left(s"Bet already placed on $selection"))
              } else if (alreadyUsed + pointsBet > 100) {
                Future.successful(Left(s"Budget exceeded: $alreadyUsed already used, ${100 - alreadyUsed} remaining"))
              } else {
                betRepository.create(PredictionBet(
                  userId = userId,
                  matchupId = matchupId,
                  selection = selection,
                  pointsBet = pointsBet
                )).map(Right(_))
              }
            }
          } yield result
      }
    } yield result
  }

  def getUserBetsForEvent(userId: Long, eventId: Long): Future[Seq[(PredictionBet, PredictionMatchup)]] = {
    for {
      matchups <- matchupRepository.findByEventId(eventId)
      matchupIds = matchups.map(_.id)
      bets <- betRepository.findByUserAndMatchupIds(userId, matchupIds)
    } yield bets.flatMap { bet =>
      matchups.find(_.id == bet.matchupId).map(bet -> _)
    }
  }

  def deleteBet(userId: Long, matchupId: Long): Future[Either[String, Unit]] = {
    for {
      matchupOpt <- matchupRepository.findById(matchupId)
      result <- matchupOpt match {
        case None =>
          Future.successful(Left("Matchup not found"))
        case Some(matchup) if matchup.status != PredictionMatchupStatus.Open =>
          Future.successful(Left("Cannot modify bets on a locked/scored matchup"))
        case Some(_) =>
          betRepository.deleteByUserAndMatchup(userId, matchupId).map(_ => Right(()))
      }
    } yield result
  }

  def scoreMatchup(matchupId: Long, result: JsValue): Future[Either[String, PredictionMatchup]] = {
    val resultStr = result.toString()
    val action = for {
      matchupOpt <- matchupRepository.findByIdAction(matchupId)
      res <- matchupOpt match {
        case None =>
          DBIO.successful(Left("Matchup not found"): Either[String, PredictionMatchup])
        case Some(matchup) if matchup.status != PredictionMatchupStatus.Locked =>
          DBIO.successful(Left("Matchup must be locked before scoring"): Either[String, PredictionMatchup])
        case Some(matchup) =>
          val parsedResult = matchup.betType match {
            case PredictionBetType.GroupPosition =>
              (result \ "positions").asOpt[Map[String, String]] match {
                case Some(_) => Right(result)
                case None => Left("Missing 'positions' in result JSON")
              }
            case PredictionBetType.MatchScore =>
              (result \ "score").asOpt[String] match {
                case Some(_) => Right(result)
                case None => Left("Missing 'score' in result JSON")
              }
          }
          parsedResult match {
            case Left(err) =>
              DBIO.successful(Left(err): Either[String, PredictionMatchup])
            case Right(_) =>
              for {
                _ <- sqlu"UPDATE prediction_matchups SET result = $resultStr, status = 'Scored', result_scored_at = NOW() WHERE id = $matchupId"
                bets <- betRepository.findByMatchupAction(matchupId)
                winningBets = calculateWinners(matchup, bets, result)
                _ <- DBIO.sequence(winningBets.map { case (bet, winnings) =>
                  scoreRepository.addScoreAction(bet.userId, matchup.eventId, winnings)
                })
              } yield Right(matchup.copy(result = Some(result), status = PredictionMatchupStatus.Scored)): Either[String, PredictionMatchup]
          }
      }
    } yield res

    db.run(action.transactionally)
  }

  private def calculateWinners(matchup: PredictionMatchup, bets: Seq[PredictionBet], result: JsValue): Seq[(PredictionBet, Int)] = {
    val isCorrect = matchup.betType match {
      case PredictionBetType.GroupPosition =>
        (selection: String, res: JsValue) =>
          val positions = (res \ "positions").as[Map[String, String]]
          val parts = selection.split("-position")
          if (parts.length == 2) {
            val player = parts(0)
            val position = parts(1)
            positions.get(position).contains(player)
          } else false

      case PredictionBetType.MatchScore =>
        (selection: String, res: JsValue) =>
          val score = (res \ "score").as[String]
          selection == score
    }

    bets.flatMap { bet =>
      if (isCorrect(bet.selection, result)) {
        val multiplier = matchup.multipliers.getOrElse(bet.selection, 0.0)
        val winnings = (bet.pointsBet * multiplier).toInt
        Some((bet, winnings))
      } else None
    }
  }

  def getLeaderboard(eventId: Long): Future[Seq[PredictionUserScoreWithUsername]] = {
    scoreRepository.findLeaderboard(eventId)
  }
}
