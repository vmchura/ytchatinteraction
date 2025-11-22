package models.repository

import models.{AnalyticalFile, StarCraftModels}
import models.component.AnalyticalFileComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AnalyticalFileRepository {
  def create(analyticalFile: AnalyticalFile): Future[AnalyticalFile]

  def findById(id: Long): Future[Option[AnalyticalFile]]

  def findByUserId(userId: Long): Future[Seq[AnalyticalFile]]

  def findBySha256Hash(sha256Hash: String): Future[Option[AnalyticalFile]]

  def findByUserRace(userId: Long, race: StarCraftModels.SCRace): Future[Seq[AnalyticalFile]]

  def findByRivalRace(userId: Long, race: StarCraftModels.SCRace): Future[Seq[AnalyticalFile]]

  def findByMatchup(userId: Long, userRace: StarCraftModels.SCRace, rivalRace: StarCraftModels.SCRace): Future[Seq[AnalyticalFile]]

  def deleteById(id: Long): Future[Int]
}

@Singleton
class AnalyticalFileRepositoryImpl @Inject()(
                                              dbConfigProvider: DatabaseConfigProvider
                                            )(implicit ec: ExecutionContext) extends AnalyticalFileRepository with AnalyticalFileComponent {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile

  import dbConfig._
  import profile.api._

  def getTableQuery = analyticalFilesTable

  override def create(analyticalFile: AnalyticalFile): Future[AnalyticalFile] = {
    val insertQuery = analyticalFilesTable returning analyticalFilesTable.map(_.id) into ((analyticalFile, id) => analyticalFile.copy(id = id))
    db.run(insertQuery += analyticalFile)
  }

  override def findById(id: Long): Future[Option[AnalyticalFile]] = {
    db.run(analyticalFilesTable.filter(_.id === id).result.headOption)
  }

  override def findByUserId(userId: Long): Future[Seq[AnalyticalFile]] = {
    db.run(analyticalFilesTable.filter(_.userId === userId).sortBy(_.uploadedAt.desc).result)
  }

  override def findBySha256Hash(sha256Hash: String): Future[Option[AnalyticalFile]] = {
    db.run(analyticalFilesTable.filter(_.sha256Hash === sha256Hash).result.headOption)
  }

  override def findByUserRace(userId: Long, race: StarCraftModels.SCRace): Future[Seq[AnalyticalFile]] = {
    db.run(analyticalFilesTable.filter(f => f.userId === userId && f.userRace === race).sortBy(_.uploadedAt.desc).result)
  }

  override def findByRivalRace(userId: Long, race: StarCraftModels.SCRace): Future[Seq[AnalyticalFile]] = {
    db.run(analyticalFilesTable.filter(f => f.userId === userId && f.rivalRace === race).sortBy(_.uploadedAt.desc).result)
  }

  override def findByMatchup(userId: Long, userRace: StarCraftModels.SCRace, rivalRace: StarCraftModels.SCRace): Future[Seq[AnalyticalFile]] = {
    db.run(analyticalFilesTable.filter(f => f.userId === userId && f.userRace === userRace && f.rivalRace === rivalRace).sortBy(_.uploadedAt.desc).result)
  }

  override def deleteById(id: Long): Future[Int] = {
    db.run(analyticalFilesTable.filter(_.id === id).delete)
  }
}