package models

import play.api.libs.json._
import java.time.Instant

case class UserAliasHistory(
  id: Long,
  userId: Long,
  alias: String,
  isCurrent: Boolean,
  assignedAt: Instant,
  replacedAt: Option[Instant] = None,
  generationMethod: String
)