package models.component

import models.YoutubeChatMessage
import play.api.libs.json.JsValue
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

import java.time.Instant

trait YoutubeChatMessageComponent {
  protected val profile: JdbcProfile
  import profile.api._

  // Custom column type for JSON
  

  given BaseColumnType[Instant] = MappedColumnType.base[Instant, java.sql.Timestamp](
    instant => java.sql.Timestamp.from(instant),
    timestamp => timestamp.toInstant
  )

  class YoutubeChatMessagesTable(tag: Tag) extends Table[YoutubeChatMessage](tag, "youtube_chat_messages") {
    def messageId = column[Int]("message_id", O.PrimaryKey, O.AutoInc)
    def liveChatId = column[String]("live_chat_id")
    def channelId = column[String]("channel_id")
    def rawMessage = column[String]("raw_message") // JSON column
    def authorChannelId = column[String]("author_channel_id")
    def authorDisplayName = column[String]("author_display_name")
    def messageText = column[String]("message_text")
    def publishedAt = column[Instant]("published_at")
    def createdAt = column[Instant]("created_at", O.Default(Instant.now()))
    
    // Indexes for faster queries
    def idx_live_chat_id = index("idx_youtube_chat_messages_live_chat_id", liveChatId)
    def idx_channel_id = index("idx_youtube_chat_messages_channel_id", channelId)
    def idx_author_channel_id = index("idx_youtube_chat_messages_author_channel_id", authorChannelId)
    def idx_published_at = index("idx_youtube_chat_messages_published_at", publishedAt)
    
    def * = (
      messageId.?,
      liveChatId,
      channelId,
      rawMessage,
      authorChannelId,
      authorDisplayName,
      messageText,
      publishedAt,
      createdAt.?
    ) <> ((YoutubeChatMessage.apply _).tupled, YoutubeChatMessage.unapply)
  }

  val youtubeChatMessagesTable = TableQuery[YoutubeChatMessagesTable]
}
