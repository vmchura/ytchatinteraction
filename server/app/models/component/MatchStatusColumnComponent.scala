package models.component

import models.MatchStatus
import slick.jdbc.JdbcProfile

trait MatchStatusColumnComponent {
  protected val profile: JdbcProfile
  import profile.api.*

  given BaseColumnType[MatchStatus] =
    MatchStatus.columnType
}
