package models

import play.api.libs.json._

case class User(userId: Long, userName: String)

object User {
  implicit val userFormat: OFormat[User] = Json.format[User]
}