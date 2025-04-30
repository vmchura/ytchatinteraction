package models.repository

import models.component.{UserComponent, UserStreamerStateLogComponent, YtStreamerComponent}
import models.UserStreamerStateLog
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class UserStreamerStateLogRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends UserStreamerStateLogComponent with UserComponent with YtStreamerComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._
  
  /**
   * Create a log entry for user streamer state changes
   */
  def createAction(log: UserStreamerStateLog): DBIO[UserStreamerStateLog] = {
    val logWithTimestamp = log.copy(createdAt = Some(Instant.now()))
    
    (userStreamerStateLogTable returning userStreamerStateLogTable.map(_.logId)
      into ((l, id) => l.copy(logId = Some(id)))
    ) += logWithTimestamp
  }
  
  def create(log: UserStreamerStateLog): Future[UserStreamerStateLog] = db.run {
    createAction(log)
  }
  
}
