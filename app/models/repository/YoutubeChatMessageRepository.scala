package models.repository

import javax.inject.{Inject, Singleton}
import models.YoutubeChatMessage
import models.component.YoutubeChatMessageComponent
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.JsValue
import slick.jdbc.JdbcProfile

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class YoutubeChatMessageRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends YoutubeChatMessageComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._
  
  /**
   * Create a new YouTube chat message
   */
  def create(message: YoutubeChatMessage): Future[YoutubeChatMessage] = db.run {
    val messageWithTimestamp = message.copy(
      createdAt = Some(Instant.now())
    )
    
    (youtubeChatMessagesTable returning youtubeChatMessagesTable.map(_.messageId)
      into ((msg, id) => msg.copy(messageId = Some(id)))
    ) += messageWithTimestamp
  }
  
  /**
   * Create multiple YouTube chat messages in a batch
   */
  def createBatch(messages: Seq[YoutubeChatMessage]): Future[Unit] = db.run {
    val messagesWithTimestamps = messages.map(msg => 
      msg.copy(createdAt = Some(Instant.now()))
    )
    
    youtubeChatMessagesTable ++= messagesWithTimestamps
  }.map(_ => ())
  
  /**
   * Get a message by ID
   */
  def getById(messageId: Int): Future[Option[YoutubeChatMessage]] = db.run {
    youtubeChatMessagesTable.filter(_.messageId === messageId).result.headOption
  }
  
  /**
   * Get all messages for a live chat
   */
  def getByLiveChatId(liveChatId: String): Future[Seq[YoutubeChatMessage]] = db.run {
    youtubeChatMessagesTable.filter(_.liveChatId === liveChatId).result
  }
  
  /**
   * Get messages for a live chat ordered by published time
   */
  def getByLiveChatIdOrdered(liveChatId: String, limit: Int = 100): Future[Seq[YoutubeChatMessage]] = db.run {
    youtubeChatMessagesTable
      .filter(_.liveChatId === liveChatId)
      .sortBy(_.publishedAt.desc)
      .take(limit)
      .result
  }
  
  /**
   * Get all messages for a channel
   */
  def getByChannelId(channelId: String, limit: Int = 100): Future[Seq[YoutubeChatMessage]] = db.run {
    youtubeChatMessagesTable
      .filter(_.channelId === channelId)
      .sortBy(_.publishedAt.desc)
      .take(limit)
      .result
  }
  
  /**
   * Get messages by author
   */
  def getByAuthorChannelId(authorChannelId: String, limit: Int = 100): Future[Seq[YoutubeChatMessage]] = db.run {
    youtubeChatMessagesTable
      .filter(_.authorChannelId === authorChannelId)
      .sortBy(_.publishedAt.desc)
      .take(limit)
      .result
  }
  
  /**
   * Delete old messages to prevent database growth
   * Keeps only the most recent messages up to a certain date
   */
  def deleteOldMessages(beforeDate: Instant): Future[Int] = db.run {
    youtubeChatMessagesTable.filter(_.publishedAt < beforeDate).delete
  }
  
  
  /**
   * Search for messages containing specific text in their message content
   */
  def searchByMessage(searchText: String, limit: Int = 100): Future[Seq[YoutubeChatMessage]] = db.run {
    youtubeChatMessagesTable
      .filter(_.messageText.like(s"%${searchText}%"))
      .sortBy(_.publishedAt.desc)
      .take(limit)
      .result
  }
  
  /**
   * Get message statistics for a channel
   * Returns total message count, unique author count, and average messages per author
   */
  def getChannelStatistics(channelId: String): Future[Map[String, Double]] = db.run {
    for {
      totalCount <- youtubeChatMessagesTable.filter(_.channelId === channelId).length.result
      uniqueAuthors <- youtubeChatMessagesTable.filter(_.channelId === channelId).map(_.authorChannelId).distinct.length.result
    } yield {
      Map(
        "totalMessages" -> totalCount.toDouble,
        "uniqueAuthors" -> uniqueAuthors.toDouble,
        "avgMessagesPerAuthor" -> (if (uniqueAuthors > 0) totalCount.toDouble / uniqueAuthors.toDouble else 0)
      )
    }
  }
}
