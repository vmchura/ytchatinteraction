package models.repository

import models.{PredictionMatchup, PredictionMatchupStatus, PredictionBetType}
import models.component.PredictionMatchupComponent
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.JsValue
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class PredictionMatchupRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends PredictionMatchupComponent {

  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  def create(matchup: PredictionMatchup): Future[PredictionMatchup] = db.run {
    createAction(matchup)
  }

  def createAction(matchup: PredictionMatchup): DBIO[PredictionMatchup] = {
    (predictionMatchupsTable returning predictionMatchupsTable.map(_.id) into ((m, id) => m.copy(id = id))) += matchup
  }

  def findById(id: Long): Future[Option[PredictionMatchup]] = db.run {
    findByIdAction(id)
  }

  def findByIdAction(id: Long): DBIO[Option[PredictionMatchup]] = {
    predictionMatchupsTable.filter(_.id === id).result.headOption
  }

  def findByEventId(eventId: Long): Future[Seq[PredictionMatchup]] = db.run {
    findByEventIdAction(eventId)
  }

  def findByEventIdAction(eventId: Long): DBIO[Seq[PredictionMatchup]] = {
    predictionMatchupsTable.filter(_.eventId === eventId).sortBy(m => (m.round.asc, m.matchLabel.asc)).result
  }

  def findByEventIdAndRound(eventId: Long, round: Int): Future[Seq[PredictionMatchup]] = db.run {
    predictionMatchupsTable
      .filter(m => m.eventId === eventId && m.round === round)
      .sortBy(_.matchLabel.asc)
      .result
  }

  def update(matchup: PredictionMatchup): Future[Option[PredictionMatchup]] = db.run {
    updateAction(matchup)
  }

  def updateAction(matchup: PredictionMatchup): DBIO[Option[PredictionMatchup]] = {
    for {
      rowsUpdated <- predictionMatchupsTable.filter(_.id === matchup.id).update(matchup)
      result <- if (rowsUpdated > 0) DBIO.successful(Some(matchup)) else DBIO.successful(None)
    } yield result
  }

  def updateStatus(id: Long, status: PredictionMatchupStatus): Future[Option[PredictionMatchup]] = db.run {
    for {
      rowsUpdated <- predictionMatchupsTable.filter(_.id === id).map(_.status).update(status)
      result <- if (rowsUpdated > 0) findByIdAction(id) else DBIO.successful(None)
    } yield result
  }

  def updateResult(id: Long, result: JsValue): Future[Option[PredictionMatchup]] = db.run {
    val resultStr = result.toString()
    for {
      _ <- sqlu"UPDATE prediction_matchups SET result = $resultStr::jsonb, status = 'Scored', result_scored_at = NOW() WHERE id = $id"
      updated <- findByIdAction(id)
    } yield updated
  }

  def delete(id: Long): Future[Boolean] = db.run {
    predictionMatchupsTable.filter(_.id === id).delete.map(_ > 0)
  }
}
