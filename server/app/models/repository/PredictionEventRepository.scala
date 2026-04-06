package models.repository

import models.{PredictionEvent, PredictionEventStatus}
import models.component.PredictionEventComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PredictionEventRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends PredictionEventComponent {

  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  def create(event: PredictionEvent): Future[PredictionEvent] = db.run {
    createAction(event)
  }

  def createAction(event: PredictionEvent): DBIO[PredictionEvent] = {
    (predictionEventsTable returning predictionEventsTable.map(_.id) into ((e, id) => e.copy(id = id))) += event
  }

  def findById(id: Long): Future[Option[PredictionEvent]] = db.run {
    findByIdAction(id)
  }

  def findByIdAction(id: Long): DBIO[Option[PredictionEvent]] = {
    predictionEventsTable.filter(_.id === id).result.headOption
  }

  def findAll(): Future[Seq[PredictionEvent]] = db.run {
    predictionEventsTable.sortBy(_.createdAt.desc).result
  }

  def update(event: PredictionEvent): Future[Option[PredictionEvent]] = db.run {
    updateAction(event)
  }

  def updateAction(event: PredictionEvent): DBIO[Option[PredictionEvent]] = {
    for {
      rowsUpdated <- predictionEventsTable.filter(_.id === event.id).update(event)
      result <- if (rowsUpdated > 0) DBIO.successful(Some(event)) else DBIO.successful(None)
    } yield result
  }

  def updateStatus(id: Long, status: PredictionEventStatus): Future[Option[PredictionEvent]] = db.run {
    for {
      rowsUpdated <- predictionEventsTable.filter(_.id === id).map(_.status).update(status)
      result <- if (rowsUpdated > 0) findByIdAction(id) else DBIO.successful(None)
    } yield result
  }

  def delete(id: Long): Future[Boolean] = db.run {
    predictionEventsTable.filter(_.id === id).delete.map(_ > 0)
  }
}
