package models

import play.api.libs.json._
import java.time.Instant

case class YtUser(
  userChannelId: String, 
  userId: Long, 
  displayName: Option[String] = None, 
  email: Option[String] = None, 
  profileImageUrl: Option[String] = None,
  activated: Boolean = false,
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now()
)

object YtUser {
  implicit val instantFormat: Format[Instant] = new Format[Instant] {
    def reads(json: JsValue): JsResult[Instant] = json match {
      case JsString(s) => JsSuccess(Instant.parse(s))
      case JsNumber(n) => JsSuccess(Instant.ofEpochMilli(n.toLong))
      case _ => JsError("Instant format error")
    }
    def writes(instant: Instant): JsValue = JsString(instant.toString)
  }

  implicit val ytUserFormat: OFormat[YtUser] = Json.format[YtUser]
}