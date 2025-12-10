package models.component

import models.{CasualMatch, MatchStatus}
import slick.jdbc.JdbcProfile
import java.time.Instant

trait CasualMatchComponent {
  protected val profile: JdbcProfile
  
  import profile.api._
  given BaseColumnType[MatchStatus] = MatchStatus.columnType

  class CasualMatchesTable(tag: Tag) extends Table[CasualMatch](tag, "casual_match") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def rivalUserId = column[Long]("rival_user_id")
    def winnerUserId = column[Option[Long]]("winner_user_id")
    def createdAt = column[Instant]("created_at")
    def status = column[MatchStatus]("status")

    def * = (id, userId, rivalUserId, winnerUserId, createdAt, status).mapTo[CasualMatch]

  }

  lazy val casualMatchesTable = TableQuery[CasualMatchesTable]
}
