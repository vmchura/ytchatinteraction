package models.repository

import models.{CasualMatchFile, StarCraftModels}
import models.component.CasualMatchFileComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait CasualMatchFileRepository {
  def create(casualMatchFile: CasualMatchFile): Future[CasualMatchFile]
  def findById(id: Long): Future[Option[CasualMatchFile]]
  def findByCasualMatchId(casualMatchId: Long): Future[Seq[CasualMatchFile]]
  def findBySha256Hash(sha256Hash: String): Future[Option[CasualMatchFile]]
  def findByMatchup(casualMatchId: Long, userRace: StarCraftModels.SCRace, rivalRace: StarCraftModels.SCRace): Future[Seq[CasualMatchFile]]
  def deleteById(id: Long): Future[Int]
  def deleteByCasualMatchId(casualMatchId: Long): Future[Int]
}

@Singleton
class CasualMatchFileRepositoryImpl @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends CasualMatchFileRepository with CasualMatchFileComponent {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  
  import dbConfig._
  import profile.api._

  def getTableQuery = casualMatchFilesTable

  override def create(casualMatchFile: CasualMatchFile): Future[CasualMatchFile] = {
    val insertQuery = casualMatchFilesTable returning casualMatchFilesTable.map(_.id) into ((casualMatchFile, id) => casualMatchFile.copy(id = id))
    db.run(insertQuery += casualMatchFile)
  }

  override def findById(id: Long): Future[Option[CasualMatchFile]] = {
    db.run(casualMatchFilesTable.filter(_.id === id).result.headOption)
  }

  override def findByCasualMatchId(casualMatchId: Long): Future[Seq[CasualMatchFile]] = {
    db.run(casualMatchFilesTable.filter(_.casualMatchId === casualMatchId).sortBy(_.uploadedAt.desc).result)
  }

  override def findBySha256Hash(sha256Hash: String): Future[Option[CasualMatchFile]] = {
    db.run(casualMatchFilesTable.filter(_.sha256Hash === sha256Hash).result.headOption)
  }

  override def findByMatchup(casualMatchId: Long, userRace: StarCraftModels.SCRace, rivalRace: StarCraftModels.SCRace): Future[Seq[CasualMatchFile]] = {
    db.run(casualMatchFilesTable.filter(f => f.casualMatchId === casualMatchId && f.userRace === userRace && f.rivalRace === rivalRace).sortBy(_.uploadedAt.desc).result)
  }

  override def deleteById(id: Long): Future[Int] = {
    db.run(casualMatchFilesTable.filter(_.id === id).delete)
  }

  override def deleteByCasualMatchId(casualMatchId: Long): Future[Int] = {
    db.run(casualMatchFilesTable.filter(_.casualMatchId === casualMatchId).delete)
  }
}
