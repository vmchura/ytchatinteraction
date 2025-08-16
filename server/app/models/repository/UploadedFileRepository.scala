package models.repository

import models.UploadedFile
import models.component.UploadedFileComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

trait UploadedFileRepository {
  def create(uploadedFile: UploadedFile): Future[UploadedFile]
  def findById(id: Long): Future[Option[UploadedFile]]
  def findByUserId(userId: Long): Future[Seq[UploadedFile]]
  def findByMatchId(matchId: Long): Future[Seq[UploadedFile]]
  def findByTournamentId(tournamentId: Long): Future[Seq[UploadedFile]]
  def findBySha256Hash(sha256Hash: String): Future[Option[UploadedFile]]
  def findByUserAndMatch(userId: Long, matchId: Long): Future[Seq[UploadedFile]]
  def deleteById(id: Long): Future[Int]
  def deleteByMatchId(matchId: Long): Future[Int]
}

@Singleton
class UploadedFileRepositoryImpl @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends UploadedFileRepository with UploadedFileComponent {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  
  import dbConfig._
  import profile.api._

  // Expose the TableQuery for foreign key references
  def getTableQuery = uploadedFilesTable

  override def create(uploadedFile: UploadedFile): Future[UploadedFile] = {
    val insertQuery = uploadedFilesTable returning uploadedFilesTable.map(_.id) into ((uploadedFile, id) => uploadedFile.copy(id = id))
    db.run(insertQuery += uploadedFile)
  }

  override def findById(id: Long): Future[Option[UploadedFile]] = {
    db.run(uploadedFilesTable.filter(_.id === id).result.headOption)
  }

  override def findByUserId(userId: Long): Future[Seq[UploadedFile]] = {
    db.run(uploadedFilesTable.filter(_.userId === userId).sortBy(_.uploadedAt.desc).result)
  }

  override def findByMatchId(matchId: Long): Future[Seq[UploadedFile]] = {
    db.run(uploadedFilesTable.filter(_.matchId === matchId).sortBy(_.uploadedAt.desc).result)
  }

  override def findByTournamentId(tournamentId: Long): Future[Seq[UploadedFile]] = {
    db.run(uploadedFilesTable.filter(_.tournamentId === tournamentId).sortBy(_.uploadedAt.desc).result)
  }

  override def findBySha256Hash(sha256Hash: String): Future[Option[UploadedFile]] = {
    db.run(uploadedFilesTable.filter(_.sha256Hash === sha256Hash).result.headOption)
  }

  override def findByUserAndMatch(userId: Long, matchId: Long): Future[Seq[UploadedFile]] = {
    db.run(uploadedFilesTable.filter(f => f.userId === userId && f.matchId === matchId).sortBy(_.uploadedAt.desc).result)
  }

  override def deleteById(id: Long): Future[Int] = {
    db.run(uploadedFilesTable.filter(_.id === id).delete)
  }

  override def deleteByMatchId(matchId: Long): Future[Int] = {
    db.run(uploadedFilesTable.filter(_.matchId === matchId).delete)
  }
}