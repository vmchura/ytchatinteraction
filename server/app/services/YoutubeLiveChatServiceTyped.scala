package services

import javax.inject.{Inject, Singleton}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.actor.typed.scaladsl.adapter.*
import play.api.libs.ws.WSClient
import play.api.Configuration
import models.*
import models.repository.{PollVoteRepository, UserRepository, UserStreamerStateRepository, YoutubeChatMessageRepository, YtStreamerRepository, YtUserRepository}
import actors.YoutubeLiveChatPollingActor

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/**
 * Service for handling YouTube Live Chat polling.
 * Uses Pekko Typed actors for communication.
 */
@Singleton
class YoutubeLiveChatServiceTyped @Inject()(
  ws: WSClient,
  config: Configuration,
  ytStreamerRepository: YtStreamerRepository,
  userStreamerStateRepository: UserStreamerStateRepository,
  userService: UserService,
  ytUserRepository: YtUserRepository,
  actorSystem: ActorSystem[Nothing],
  pollService: PollService,
  inferUserOptionService: InferUserOptionService,
  chatService: ChatService,
  pollVoteRepository: PollVoteRepository,
  activeLiveStream: ActiveLiveStream,
  youtubeChatMessageRepository: YoutubeChatMessageRepository
)(implicit ec: ExecutionContext) {
  
  // Get API key from configuration
  private val apiKey = config.get[String]("youtube.api.key")
  
  /**
   * Start monitoring the live chat for a specific streamer
   * @param streamerChatId The YouTube live chat ID to monitor
   * @return Future that completes when the initial polling is set up
   */
  def startMonitoringLiveChat(streamerChatId: String, chanelID: String, title: String): Future[Boolean] = {
    // Get current time to filter messages
    val startTime = Instant.now()
    
    // Create the actor for polling YouTube Live Chat
    val chatPollingActor = actorSystem.systemActorOf(
      YoutubeLiveChatPollingActor(
        ws, 
        apiKey, 
        ytStreamerRepository, 
        userStreamerStateRepository,
        userService,
        ytUserRepository, 
        startTime, 
        pollService, 
        inferUserOptionService, 
        chatService,
        pollVoteRepository,
        activeLiveStream,
        youtubeChatMessageRepository
      ),
      s"youtube-chat-polling-${streamerChatId}"
    )
    
    // Send the initial message to start polling with 0 retry count
    chatPollingActor ! YoutubeLiveChatPollingActor.PollLiveChat(streamerChatId, chanelID, null, 0)
    activeLiveStream.addElement(streamerChatId, title)
    // Return a completed future
    Future.successful(true)
  }
  
  /**
   * Stop monitoring the live chat for a specific streamer
   * @param streamerChatId The YouTube live chat ID to stop monitoring
   */
  def stopMonitoringLiveChat(streamerChatId: String): Unit = {
    // In a more complete implementation, we would track and stop the actor
    // For now, we're relying on the actor to stop itself when the chat ends
  }
  
  /**
   * Retrieves the live chat ID for a given YouTube stream ID
   * This method can be reused from the original service implementation
   * @param streamId The YouTube video/stream ID
   * @return Future containing the live chat ID if found
   */
  def getLiveChatId(streamId: String): Future[Option[LiveChat]] = {
    val url = "https://www.googleapis.com/youtube/v3/videos"
    
    val queryParams = Map(
      "part" -> "liveStreamingDetails,snippet",
      "id" -> streamId,
      "key" -> apiKey
    )
    
    ws.url(url)
      .withQueryStringParameters(queryParams.toSeq: _*)
      .get()
      .flatMap { response =>

        import play.api.libs.json._
        val json = response.json
        println(json)
        // Check if the request was successful and items exist
        val items = (json \ "items").as[JsArray].value
        if (items.isEmpty) {
          Future.successful(None)
        } else {
          // Extract channel ID, channel title, and live chat ID from the first item
          val channelId = (items.head \ "snippet" \ "channelId").asOpt[String]
          val channelTitle = (items.head \ "snippet" \ "channelTitle").asOpt[String]
          val title = (items.head \ "snippet" \ "title").asOpt[String].getOrElse(channelTitle.getOrElse("?"))
          val liveChatId = (items.head \ "liveStreamingDetails" \ "activeLiveChatId").asOpt[String]
          
          // If we have both channel ID and live chat ID
          (channelId, liveChatId) match {
            case (Some(cid), Some(lcid)) =>
              val response = LiveChat(cid, lcid, title)
              // Check if this channel exists as a YtStreamer
              ytStreamerRepository.getByChannelId(cid).flatMap {
                case None =>
                  // Case 3: New channel found during live chat ID lookup - create YtStreamer with null owner
                  ytStreamerRepository.create(cid, None, 0, channelTitle).map(_ => Some(response))
                case Some(existingStreamer) =>
                  // Channel already exists, update the channel title if needed and return the live chat ID
                  if (channelTitle.isDefined && existingStreamer.channelTitle != channelTitle) {
                    ytStreamerRepository.updateChannelTitle(cid, channelTitle).map(_ => Some(response))
                  } else {
                    Future.successful(Some(response))
                  }
              }
            case _ => 
              // No live chat ID found
              Future.successful(None)
          }
        }
      }
      .recover {
        case e: Exception =>
          println(s"Error retrieving live chat ID: ${e.getMessage}")
          None
      }
  }
}