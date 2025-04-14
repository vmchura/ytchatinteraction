package models

import play.api.libs.json._

case class YtUser(userChannelId: String, userId: Long)

object YtUser {
  implicit val ytUserFormat: OFormat[YtUser] = Json.format[YtUser]
}