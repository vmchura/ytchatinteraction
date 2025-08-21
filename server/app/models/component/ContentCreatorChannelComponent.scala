package models.component

import models.ContentCreatorChannel
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

import java.time.Instant

trait ContentCreatorChannelComponent {

  protected val profile: JdbcProfile
  import profile.api.*

  class ContentCreatorChannelTable(tag: Tag) extends Table[ContentCreatorChannel](tag, "content_creator_channels") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def youtubeChannelId = column[String]("youtube_channel_id")
    def youtubeChannelName = column[String]("youtube_channel_name")
    def isActive = column[Boolean]("is_active")
    def updatedAt = column[Instant]("updated_at")

    def * = (
      id.?,
      youtubeChannelId,
      youtubeChannelName,
      isActive,
      updatedAt
    ) <> ((ContentCreatorChannel.apply _).tupled, ContentCreatorChannel.unapply)
  }

  val contentCreatorChannelsTable = TableQuery[ContentCreatorChannelTable]
}