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
   * Retrieves a content creator channel by YouTube channel ID.
   *
   * @param youtubeChannelId The YouTube channel ID
   * @return The channel if found, None otherwise
   */
  def getContentCreatorChannelByYoutubeId(youtubeChannelId: String): Future[Option[ContentCreatorChannel]]

  /**
   * Retrieves all content creator channels.
   *
   * @return List of all channels
   */
  def getAllContentCreatorChannels: Future[List[ContentCreatorChannel]]

  /**
   * Retrieves all active content creator channels.
   *
   * @return List of active channels
   */
  def getActiveContentCreatorChannels: Future[List[ContentCreatorChannel]]

  /**
   * Updates a content creator channel.
   *
   * @param channel The channel to update
   * @return The updated channel if successful, error message otherwise
   */
  def updateContentCreatorChannel(channel: ContentCreatorChannel): Future[Either[String, ContentCreatorChannel]]

  /**
   * Activates or deactivates a content creator channel.
   *
   * @param id The channel ID
   * @param active The new active status
   * @return The updated channel if successful, error message otherwise
   */
  def setChannelActiveStatus(id: Long, active: Boolean): Future[Either[String, ContentCreatorChannel]]

  /**
   * Deletes a content creator channel.
   *
   * @param id The channel ID
   * @return True if deletion was successful, false otherwise
   */
  def deleteContentCreatorChannel(id: Long): Future[Boolean]

  /**
   * Checks if a YouTube channel is registered as a content creator.
   *
   * @param youtubeChannelId The YouTube channel ID
   * @return True if registered, false otherwise
   */
  def isRegisteredContentCreator(youtubeChannelId: String): Future[Boolean]

  /**
   * Gets statistics about content creator channels.
   *
   * @return (total count, active count)
   */
  def getChannelStatistics: Future[(Int, Int)]
}

@Singleton
class ContentCreatorChannelServiceImpl @Inject()(
                                                  contentCreatorChannelRepository: ContentCreatorChannelRepository
                                                )(implicit ec: ExecutionContext) extends ContentCreatorChannelService {

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

  override def getContentCreatorChannelByYoutubeId(youtubeChannelId: String): Future[Option[ContentCreatorChannel]] = {
    contentCreatorChannelRepository.findByYoutubeChannelId(youtubeChannelId)
  }

  override def getAllContentCreatorChannels: Future[List[ContentCreatorChannel]] = {
    contentCreatorChannelRepository.findAll()
  }

  override def getActiveContentCreatorChannels: Future[List[ContentCreatorChannel]] = {
    contentCreatorChannelRepository.findAllActive()
  }

  override def updateContentCreatorChannel(channel: ContentCreatorChannel): Future[Either[String, ContentCreatorChannel]] = {
    if (channel.youtubeChannelName.trim.isEmpty) {
      return Future.successful(Left("Channel name cannot be empty"))
    }

    contentCreatorChannelRepository.update(channel).map {
      case Some(updatedChannel) => Right(updatedChannel)
      case None => Left("Content creator channel not found")
    }
  }

  override def setChannelActiveStatus(id: Long, active: Boolean): Future[Either[String, ContentCreatorChannel]] = {
    contentCreatorChannelRepository.updateActiveStatus(id, active).map {
      case Some(updatedChannel) => Right(updatedChannel)
      case None => Left("Content creator channel not found")
    }
  }

  override def deleteContentCreatorChannel(id: Long): Future[Boolean] = {
    contentCreatorChannelRepository.delete(id)
  }

  override def isRegisteredContentCreator(youtubeChannelId: String): Future[Boolean] = {
    contentCreatorChannelRepository.findByYoutubeChannelId(youtubeChannelId).map {
      case Some(channel) => channel.isActive
      case None => false
    }
  }

  override def getChannelStatistics: Future[(Int, Int)] = {
    for {
      totalCount <- contentCreatorChannelRepository.count()
      activeCount <- contentCreatorChannelRepository.countActive()
    } yield (totalCount, activeCount)
  }
}