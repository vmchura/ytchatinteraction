package models.repository

import models.UserAliasHistory
import models.component.UserAliasComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class UserAliasRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)
                                  (implicit ec: ExecutionContext) extends UserAliasComponent {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._

  def getUserAliasHistory(userId: Long): Future[Seq[UserAliasHistory]] = db.run {
    userAliasHistoryTable
      .filter(_.userId === userId)
      .sortBy(_.assignedAt.desc)
      .result
  }

  def getCurrentAlias(userId: Long): Future[Option[String]] = db.run {
    userAliasHistoryTable
      .filter(h => h.userId === userId && h.isCurrent)
      .map(_.alias)
      .result
      .headOption
  }

  def isAliasAvailable(alias: String): Future[Boolean] = db.run {
    userAliasHistoryTable
      .filter(h => h.alias === alias)
      .exists
      .result
      .map(!_)
  }


}
