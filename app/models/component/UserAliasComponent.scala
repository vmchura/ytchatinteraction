package models.component

import models.{TournamentStatus, UserAliasHistory}
import slick.ast.BaseTypedType
import slick.jdbc.JdbcProfile

import java.time.Instant

trait UserAliasComponent {
  protected val profile: JdbcProfile
  import profile.api._

  given BaseTypedType[Instant] = MappedColumnType.base[Instant, java.sql.Timestamp](
    { instant => java.sql.Timestamp.from(instant) },
    { timestamp => timestamp.toInstant }
  )

  class UserAliasHistoryTable(tag: Tag) extends Table[UserAliasHistory](tag, "user_alias_history") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def alias = column[String]("alias")
    def isCurrent = column[Boolean]("is_current")
    def assignedAt = column[Instant]("assigned_at")
    def replacedAt = column[Option[Instant]]("replaced_at")
    def generationMethod = column[String]("generation_method")

    def * = (id, userId, alias, isCurrent, assignedAt, replacedAt, generationMethod) <>
      ((UserAliasHistory.apply _).tupled, UserAliasHistory.unapply)
  }
  val userAliasHistoryTable = TableQuery[UserAliasHistoryTable]
}
