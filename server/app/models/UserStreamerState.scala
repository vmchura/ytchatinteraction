package models

import play.api.libs.json._

case class UserStreamerState(userId: Long, streamerChannelId: String, currentBalanceNumber: Int)

object UserStreamerState {
  implicit val userStreamerStateFormat: OFormat[UserStreamerState] = Json.format[UserStreamerState]
}