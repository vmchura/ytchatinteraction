package models.repository

import models.ContentCreatorChannel
import models.component.ContentCreatorChannelComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class ContentCreatorChannelRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends ContentCreatorChannelComponent {

  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  /**
   * Creates a new content creator channel record.
   */
  def create(channel: ContentCreatorChannel): Future[ContentCreatorChannel] = db.run {
    createAction(channel)
  }

  /**
   * Creates a new content creator channel record (DBIO action).
   */
  def createAction(channel: ContentCreatorChannel): DBIO[ContentCreatorChannel] = {
    val channelWithTimestamp = channel.copy(updatedAt = Instant.now())
    (contentCreatorChannelsTable.map(c => (c.youtubeChannelId, c.youtubeChannelName, c.isActive, c.updatedAt))
      returning contentCreatorChannelsTable.map(_.id)
      into ((fields, id) => ContentCreatorChannel(Some(id), fields._1, fields._2, fields._3, fields._4))
      ) += (channelWithTimestamp.youtubeChannelId, channelWithTimestamp.youtubeChannelName, channelWithTimestamp.isActive, channelWithTimestamp.updatedAt)
  }

  /**
   * Finds a content creator channel by its ID.
   */
  def findById(id: Long): Future[Option[ContentCreatorChannel]] = db.run {
    contentCreatorChannelsTable.filter(_.id === id).result.headOption
  }

  /**
   * Finds a content creator channel by YouTube channel ID.
   */
  def findByYoutubeChannelId(youtubeChannelId: String): Future[Option[ContentCreatorChannel]] = db.run {
    contentCreatorChannelsTable.filter(_.youtubeChannelId === youtubeChannelId).result.headOption
  }

  /**
   * Finds all content creator channels.
   */
  def findAll(): Future[List[ContentCreatorChannel]] = db.run {
    contentCreatorChannelsTable.result.map(_.toList)
  }

  /**
   * Finds all active content creator channels.
   */
  def findAllActive(): Future[List[ContentCreatorChannel]] = db.run {
    contentCreatorChannelsTable.filter(_.isActive === true).result.map(_.toList)
  }

  /**
   * Updates a content creator channel.
   */
  def update(channel: ContentCreatorChannel): Future[Option[ContentCreatorChannel]] = db.run {
    updateAction(channel)
  }

  /**
   * Updates a content creator channel (DBIO action).
   */
  def updateAction(channel: ContentCreatorChannel): DBIO[Option[ContentCreatorChannel]] = {
    channel.id match {
      case Some(id) =>
        val channelWithTimestamp = channel.copy(updatedAt = Instant.now())
        contentCreatorChannelsTable
          .filter(_.id === id)
          .update(channelWithTimestamp)
          .map {
            case 0 => None
            case _ => Some(channelWithTimestamp)
          }
      case None =>
        DBIO.successful(None)
    }
  }

  /**
   * Updates the active status of a content creator channel.
   */
  def updateActiveStatus(id: Long, active: Boolean): Future[Option[ContentCreatorChannel]] = db.run {
    contentCreatorChannelsTable
      .filter(_.id === id)
      .map(c => (c.isActive, c.updatedAt))
      .update(active, Instant.now())
      .flatMap {
        case 0 => DBIO.successful(None)
        case _ => contentCreatorChannelsTable.filter(_.id === id).result.headOption
      }
  }

  /**
   * Deletes a content creator channel by ID.
   */
  def delete(id: Long): Future[Boolean] = db.run {
    contentCreatorChannelsTable.filter(_.id === id).delete.map(_ > 0)
  }

  /**
   * Checks if a YouTube channel ID is already registered.
   */
  def exists(youtubeChannelId: String): Future[Boolean] = db.run {
    contentCreatorChannelsTable.filter(_.youtubeChannelId === youtubeChannelId).exists.result
  }

  /**
   * Gets the total count of content creator channels.
   */
  def count(): Future[Int] = db.run {
    contentCreatorChannelsTable.length.result
  }

  /**
   * Gets the count of active content creator channels.
   */
  def countActive(): Future[Int] = db.run {
    contentCreatorChannelsTable.filter(_.isActive === true).length.result
  }
}