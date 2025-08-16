package models

import java.time.Instant
import play.api.libs.json.{JsValue, Json, OFormat}

case class YoutubeChatMessage(
  messageId: Option[Int] = None,
  liveChatId: String,
  channelId: String,
  rawMessage: String,
  authorChannelId: String,
  authorDisplayName: String,
  messageText: String,
  publishedAt: Instant,
  createdAt: Option[Instant] = None
)
object YoutubeChatMessage {
  implicit val youtubeChatMessage: OFormat[YoutubeChatMessage] = Json.format[YoutubeChatMessage]
}
