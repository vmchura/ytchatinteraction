package services

import models.{
  PredictionBet,
  PredictionBetType,
  PredictionEvent,
  PredictionMatchup,
  PredictionMatchupStatus,
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

  private val groupPlayerKeys = (1 to 4).map(i => s"player$i")

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

  /**
   * Saves (or replaces) the user's prediction for a matchup. Allowed while the
   * matchup is Open; a later save overwrites the previous prediction.
   */
  def savePrediction(userId: Long, matchupId: Long, ranking: Seq[String]): Future[Either[String, PredictionBet]] = {
    for {
      matchupOpt <- matchupRepository.findById(matchupId)
      result <- matchupOpt match {
        case None =>
          Future.successful(Left("Matchup not found"))
        case Some(matchup) if matchup.status != PredictionMatchupStatus.Open =>
          Future.successful(Left("Matchup is not open for predictions"))
        case Some(matchup) =>
          validatePrediction(matchup, ranking) match {
            case Left(err) =>
              Future.successful(Left(err))
            case Right(normalized) =>
              betRepository.upsert(userId, matchupId, normalized).map(Right(_))
          }
      }
    } yield result
  }

  private def validatePrediction(matchup: PredictionMatchup, ranking: Seq[String]): Either[String, Seq[String]] = {
    matchup.betType match {
      case PredictionBetType.GroupPosition =>
        if (ranking.size != groupPlayerKeys.size) {
          Left(s"Prediction must include exactly ${groupPlayerKeys.size} players")
        } else if (ranking.distinct.size != groupPlayerKeys.size) {
          Left("Each player can only be in one position")
        } else if (!ranking.forall(groupPlayerKeys.contains)) {
          Left("Invalid player in prediction")
        } else {
          Right(ranking)
        }
      case PredictionBetType.MatchScore =>
        if (ranking.size != 1) {
          Left("Prediction must include exactly one score")
        } else if (!PredictionScoring.matchScores.contains(ranking.head)) {
          Left(s"Invalid score selection: ${ranking.head}")
        } else {
          Right(ranking)
        }
    }
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

  def deletePrediction(userId: Long, matchupId: Long): Future[Either[String, Unit]] = {
    for {
      matchupOpt <- matchupRepository.findById(matchupId)
      result <- matchupOpt match {
        case None =>
          Future.successful(Left("Matchup not found"))
        case Some(matchup) if matchup.status != PredictionMatchupStatus.Open =>
          Future.successful(Left("Cannot modify predictions on a locked/scored matchup"))
        case Some(_) =>
          betRepository.deleteByUserAndMatchup(userId, matchupId).map(_ => Right(()))
      }
    } yield result
  }

  /** Points a user's saved prediction earned for a matchup (None if not scored). */
  def scorePrediction(bet: PredictionBet, matchup: PredictionMatchup): Option[Int] = {
    matchup.result.flatMap(result => computeScore(bet, matchup, result))
  }

  private def computeScore(bet: PredictionBet, matchup: PredictionMatchup, result: JsValue): Option[Int] = {
    matchup.betType match {
      case PredictionBetType.GroupPosition =>
        (result \ "positions").asOpt[Map[String, String]].map { positions =>
          PredictionScoring.scoreGroup(bet.ranking, positions)
        }
      case PredictionBetType.MatchScore =>
        (result \ "score").asOpt[String].map { actual =>
          PredictionScoring.scoreMatch(bet.ranking.headOption.getOrElse(""), actual)
        }
    }
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
          validateResult(matchup, result) match {
            case Left(err) =>
              DBIO.successful(Left(err): Either[String, PredictionMatchup])
            case Right(_) =>
              for {
                _ <- sqlu"UPDATE prediction_matchups SET result = $resultStr, status = 'Scored', result_scored_at = NOW() WHERE id = $matchupId"
                bets <- betRepository.findByMatchupAction(matchupId)
                scored = bets.flatMap { bet =>
                  computeScore(bet, matchup, result).map(points => (bet.userId, points))
                }
                _ <- DBIO.sequence(scored.map { case (userId, points) =>
                  scoreRepository.addScoreAction(userId, matchup.eventId, points)
                })
              } yield Right(matchup.copy(result = Some(result), status = PredictionMatchupStatus.Scored)): Either[String, PredictionMatchup]
          }
      }
    } yield res

    db.run(action.transactionally)
  }

  private def validateResult(matchup: PredictionMatchup, result: JsValue): Either[String, JsValue] = {
    matchup.betType match {
      case PredictionBetType.GroupPosition =>
        (result \ "positions").asOpt[Map[String, String]] match {
          case Some(positions)
              if positions.size == groupPlayerKeys.size &&
                positions.values.toSeq.distinct.size == groupPlayerKeys.size &&
                positions.values.forall(groupPlayerKeys.contains) =>
            Right(result)
          case Some(_) =>
            Left("Result positions must be a permutation of the 4 players")
          case None =>
            Left("Missing 'positions' in result JSON")
        }
      case PredictionBetType.MatchScore =>
        (result \ "score").asOpt[String] match {
          case Some(score) if PredictionScoring.matchScores.contains(score) => Right(result)
          case Some(_) => Left(s"Invalid score: must be one of ${PredictionScoring.matchScores.mkString(", ")}")
          case None => Left("Missing 'score' in result JSON")
        }
    }
  }

  def getLeaderboard(eventId: Long): Future[Seq[PredictionUserScoreWithUsername]] = {
    scoreRepository.findLeaderboard(eventId)
  }
}
