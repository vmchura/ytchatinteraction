package models

import play.api.libs.json._

case class YtStreamer(
  channelId: String, 
  ownerUserId: Option[Long], 
  currentBalanceNumber: Int = 0,
  channelTitle: Option[String] = None
)

object YtStreamer {
  implicit val ytStreamerFormat: OFormat[YtStreamer] = Json.format[YtStreamer]
}