package models

import java.time.Instant

case class UserStreamerStateLog(
  logId: Option[Int] = None,
  userId: Option[Long],
  channelID: Option[String],
  eventID: Int,
  amountTransferred: Int,
  logType: String,
  createdAt: Option[Instant] = None
)
