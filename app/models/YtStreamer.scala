package models

import play.api.libs.json._

case class YtStreamer(channelId: String, ownerUserId: Long, currentBalanceNumber: Int = 0)

object YtStreamer {
  implicit val ytStreamerFormat: OFormat[YtStreamer] = Json.format[YtStreamer]
}