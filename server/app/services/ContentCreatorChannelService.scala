package services

import models.ContentCreatorChannel
import models.repository.ContentCreatorChannelRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Service interface for Content Creator Channel operations.
 */
trait ContentCreatorChannelService {

  /**
   * Creates a new content creator channel from a YouTube URL.
   *
   * @param youtubeUrl The YouTube channel URL (e.g., https://www.youtube.com/@RemastrTV)
   * @return The created channel or an error message
   */
  def createContentCreatorChannelFromUrl(youtubeUrl: String): Future[Either[String, ContentCreatorChannel]]

  /**
   * Creates a new content creator channel.
   *
   * @param youtubeChannelId The YouTube channel ID
   * @param youtubeChannelName The YouTube channel name
   * @return The created channel or an error message
   */
  def createContentCreatorChannel(youtubeChannelId: String, youtubeChannelName: String): Future[Either[String, ContentCreatorChannel]]

  /**
   * Retrieves a content creator channel by its ID.
   *
   * @param id The channel ID
   * @return The channel if found, None otherwise
   */
  def getContentCreatorChannel(id: Long): Future[Option[ContentCreatorChannel]]

  /**
   * Retrieves all content creator channels.
   *
   * @return List of all channels
   */
  def getAllContentCreatorChannels(): Future[List[ContentCreatorChannel]]

  /**
   * Activates or deactivates a content creator channel.
   *
   * @param id The channel ID
   * @param active The new active status
   * @return The updated channel if successful, error message otherwise
   */
  def setChannelActiveStatus(id: Long, active: Boolean): Future[Either[String, ContentCreatorChannel]]
}

@Singleton
class ContentCreatorChannelServiceImpl @Inject()(
  contentCreatorChannelRepository: ContentCreatorChannelRepository,
  youtubeChannelInfoService: YouTubeChannelInfoService
)(implicit ec: ExecutionContext) extends ContentCreatorChannelService {

  override def createContentCreatorChannelFromUrl(youtubeUrl: String): Future[Either[String, ContentCreatorChannel]] = {
    youtubeChannelInfoService.getChannelInfoFromUrl(youtubeUrl).flatMap {
      case Right(channelInfo) =>
        createContentCreatorChannel(channelInfo.id, channelInfo.name)
      case Left(error) =>
        Future.successful(Left(error))
    }
  }

  override def createContentCreatorChannel(youtubeChannelId: String, youtubeChannelName: String): Future[Either[String, ContentCreatorChannel]] = {
    // Validate YouTube channel ID format (24 characters, alphanumeric and some special chars)
    if (youtubeChannelId.length != 24 || !youtubeChannelId.matches("[a-zA-Z0-9_-]+")) {
      return Future.successful(Left("Invalid YouTube channel ID format"))
    }

    if (youtubeChannelName.trim.isEmpty) {
      return Future.successful(Left("Channel name cannot be empty"))
    }

    // Check if channel already exists
    contentCreatorChannelRepository.exists(youtubeChannelId).flatMap { exists =>
      if (exists) {
        Future.successful(Left("YouTube channel is already registered as a content creator"))
      } else {
        val channel = ContentCreatorChannel(
          youtubeChannelId = youtubeChannelId,
          youtubeChannelName = youtubeChannelName.trim
        )
        contentCreatorChannelRepository.create(channel).map(Right(_))
      }
    }
  }

  override def getContentCreatorChannel(id: Long): Future[Option[ContentCreatorChannel]] = {
    contentCreatorChannelRepository.findById(id)
  }

  override def getAllContentCreatorChannels(): Future[List[ContentCreatorChannel]] = {
    contentCreatorChannelRepository.findAll()
  }

  override def setChannelActiveStatus(id: Long, active: Boolean): Future[Either[String, ContentCreatorChannel]] = {
    contentCreatorChannelRepository.updateActiveStatus(id, active).map {
      case Some(updatedChannel) => Right(updatedChannel)
      case None => Left("Content creator channel not found")
    }
  }
}