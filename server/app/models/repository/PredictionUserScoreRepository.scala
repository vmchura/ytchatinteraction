package models.repository

import models.{PredictionUserScore, PredictionUserScoreWithUsername}
import models.component.{PredictionUserScoreComponent, UserComponent}
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PredictionUserScoreRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends PredictionUserScoreComponent with UserComponent {

  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  def findByUserAndEvent(userId: Long, eventId: Long): Future[Option[PredictionUserScore]] = db.run {
    findByUserAndEventAction(userId, eventId)
  }

  def findByUserAndEventAction(userId: Long, eventId: Long): DBIO[Option[PredictionUserScore]] = {
    predictionUserScoresTable
      .filter(s => s.userId === userId && s.eventId === eventId)
      .result.headOption
  }

  def findLeaderboard(eventId: Long): Future[Seq[PredictionUserScoreWithUsername]] = db.run {
    (predictionUserScoresTable
      .filter(_.eventId === eventId)
      .join(usersTable)
      .on(_.userId === _.userId))
      .map { case (score, user) => (score.userId, score.eventId, score.totalScore, user.userName) }
      .sortBy { case (_, _, totalScore, _) => totalScore.desc }
      .result
      .map(_.map { case (userId, eventId, totalScore, userName) =>
        PredictionUserScoreWithUsername(userId, eventId, totalScore, userName)
      })
  }

  def upsert(score: PredictionUserScore): Future[PredictionUserScore] = db.run {
    upsertAction(score)
  }

  def upsertAction(score: PredictionUserScore): DBIO[PredictionUserScore] = {
    sqlu"""
      INSERT INTO prediction_user_scores (user_id, event_id, total_score)
      VALUES (${score.userId}, ${score.eventId}, ${score.totalScore})
      ON CONFLICT (user_id, event_id)
      DO UPDATE SET total_score = prediction_user_scores.total_score + ${score.totalScore}
    """ >>
    DBIO.successful(score)
  }

  def addScoreAction(userId: Long, eventId: Long, points: Int): DBIO[Int] = {
    sqlu"""
      INSERT INTO prediction_user_scores (user_id, event_id, total_score)
      VALUES ($userId, $eventId, $points)
      ON CONFLICT (user_id, event_id)
      DO UPDATE SET total_score = prediction_user_scores.total_score + $points
    """
  }
}
