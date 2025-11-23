package models.repository

import models.{AnalyticalResult, StarCraftModels}
import models.component.AnalyticalResultComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AnalyticalResultRepository {
  def create(analyticalResult: AnalyticalResult): Future[AnalyticalResult]

  def findByMatchId(matchId: Long): Future[Seq[AnalyticalResult]]
}

@Singleton
class AnalyticalResultRepositoryImpl @Inject()(
                                                dbConfigProvider: DatabaseConfigProvider
                                              )(implicit ec: ExecutionContext) extends AnalyticalResultRepository with AnalyticalResultComponent {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile

  import dbConfig._
  import profile.api._

  def getTableQuery = analyticalResultsTable

  override def create(analyticalResult: AnalyticalResult): Future[AnalyticalResult] = {
    val insertQuery = analyticalResultsTable returning analyticalResultsTable.map(_.id) into ((analyticalResult, id) => analyticalResult.copy(id = id))
    db.run(insertQuery += analyticalResult)
  }


  override def findByMatchId(matchId: Long): Future[Seq[AnalyticalResult]] = {
    db.run(analyticalResultsTable.filter(_.matchId === matchId).sortBy(_.analysisFinishedAt.desc).result)
  }

}