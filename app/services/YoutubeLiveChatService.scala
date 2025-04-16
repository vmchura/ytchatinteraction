package services

import javax.inject.{Inject, Singleton}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import org.apache.pekko.actor.typed.scaladsl.adapter.*
import play.api.libs.ws.WSClient
import play.api.libs.json.*
import play.api.Configuration
import models.*
import models.repository.{UserRepository, UserStreamerStateRepository, YtStreamerRepository, YtUserRepository}

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

/**
 * Service for handling YouTube Live Chat polling
 */
@Singleton
class YoutubeLiveChatService @Inject()(
  ws: WSClient,
  config: Configuration,
  ytStreamerRepository: YtStreamerRepository,
  userStreamerStateRepository: UserStreamerStateRepository,
  userRepository: UserRepository,
  ytUserRepository: YtUserRepository,
  actorSystem: org.apache.pekko.actor.ActorSystem
)(implicit ec: ExecutionContext) {
  
  // Convert untyped ActorSystem to typed
  private val system = actorSystem.toTyped
  
  // Get API key from configuration
  private val apiKey = config.get[String]("youtube.api.key")
  
  /**
   * Retrieves the live chat ID for a given YouTube stream ID
   * @param streamId The YouTube video/stream ID
   * @return Future containing the live chat ID if found
   */
  def getLiveChatId(streamId: String): Future[Option[String]] = {
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
        val json = response.json
        
        // Check if the request was successful and items exist
        val items = (json \ "items").as[JsArray].value
        if (items.isEmpty) {
          Future.successful(None)
        } else {
          // Extract channel ID and live chat ID from the first item
          val channelId = (items.head \ "snippet" \ "channelId").asOpt[String]
          val liveChatId = (items.head \ "liveStreamingDetails" \ "activeLiveChatId").asOpt[String]
          
          // If we have both channel ID and live chat ID
          (channelId, liveChatId) match {
            case (Some(cid), Some(lcid)) =>
              // Check if this channel exists as a YtStreamer
              ytStreamerRepository.getByChannelId(cid).flatMap {
                case None =>
                  // Case 3: New channel found during live chat ID lookup - create YtStreamer with null owner
                  ytStreamerRepository.create(cid, None).map(_ => Some(lcid))
                case Some(_) =>
                  // Channel already exists, just return the live chat ID
                  Future.successful(Some(lcid))
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
  
  /**
   * Start monitoring the live chat for a specific streamer
   * @param streamerChatId The YouTube live chat ID to monitor
   * @return Future that completes when the initial polling is set up
   */
  def startMonitoringLiveChat(streamerChatId: String): Future[Boolean] = {
    // Get current time to filter messages
    val startTime = Instant.now()
    
    val chatPollingActor = system.systemActorOf(
      YoutubeLiveChatPollingActor(ws, apiKey, ytStreamerRepository, userStreamerStateRepository, userRepository, ytUserRepository, startTime),
      s"youtube-chat-polling-${streamerChatId}"
    )
    
    // Send the initial message to start polling with 0 retry count
    chatPollingActor ! YoutubeLiveChatPollingActor.PollLiveChat(streamerChatId, null, 0)
    
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
}

/**
 * Actor that handles polling the YouTube Live Chat API
 */
object YoutubeLiveChatPollingActor {
  // Messages the actor can receive
  sealed trait Command
  case class PollLiveChat(liveChatId: String, paginationToken: String, retryCount: Int = 0) extends Command
  case class ProcessApiResponse(response: JsValue, liveChatId: String, paginationToken: String, retryCount: Int) extends Command
  case class HandleError(error: Throwable, liveChatId: String, paginationToken: String, retryCount: Int) extends Command
  
  // Timer key
  private case object PollingTimer
  
  def apply(
    ws: WSClient, 
    apiKey: String, 
    ytStreamerRepository: YtStreamerRepository,
    userStreamerStateRepository: UserStreamerStateRepository,
    userRepository: UserRepository,
    ytUserRepository: YtUserRepository,
    startTime: Instant
  ): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      active(ws, apiKey, ytStreamerRepository, userStreamerStateRepository, userRepository, ytUserRepository, startTime, timers)
    }
  }
  
  private def active(
    ws: WSClient, 
    apiKey: String, 
    ytStreamerRepository: YtStreamerRepository,
    userStreamerStateRepository: UserStreamerStateRepository,
    userRepository: UserRepository,
    ytUserRepository: YtUserRepository,
    startTime: Instant,
    timers: TimerScheduler[Command]
  ): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      implicit val ec = context.executionContext
      
      message match {
        case PollLiveChat(liveChatId, paginationToken, retryCount) =>
          context.log.info(s"Polling live chat: $liveChatId, token: $paginationToken, retry: $retryCount")
          
          // Build the API URL
          val url = "https://www.googleapis.com/youtube/v3/liveChat/messages"
          
          // Build query parameters
          val queryParams = Map(
            "liveChatId" -> liveChatId,
            "part" -> "id,snippet,authorDetails",
            "maxResults" -> "20",
            "key" -> apiKey
          ) ++ (if (paginationToken != null) Map("pageToken" -> paginationToken) else Map.empty)
          
          // Make the API call
          ws.url(url)
            .withQueryStringParameters(queryParams.toSeq: _*)
            .get()
            .map(response => ProcessApiResponse(response.json, liveChatId, paginationToken, retryCount))
            .recover { case error => HandleError(error, liveChatId, paginationToken, retryCount) }
            .foreach(context.self ! _)
          
          Behaviors.same
          
        case ProcessApiResponse(response, liveChatId, currentPaginationToken, retryCount) =>
          // Process the API response
          try {
            val nextPageToken = (response \ "nextPageToken").asOpt[String]
            val pollingIntervalMs = (response \ "pollingIntervalMillis").as[Int]
            
            // Process messages - only those published after our start time
            val items = (response \ "items").as[JsArray].value
            context.log.info(s"Received ${items.size} messages, filtering for those after ${startTime}")
            
            items.foreach { item =>
              processMessage(item, liveChatId, ytStreamerRepository, userStreamerStateRepository, userRepository, ytUserRepository, startTime)
            }
            
            // Schedule next poll based on YouTube's recommended interval
            if (nextPageToken.isDefined) {
              val delay = math.max(pollingIntervalMs, 60000) // Minimum 1 minute to avoid rate limits
              context.log.info(s"Scheduling next poll in ${delay}ms")
              
              // Reset retry count since we had a successful call
              timers.startSingleTimer(
                PollingTimer, 
                PollLiveChat(liveChatId, nextPageToken.get, 0), // Reset retry count
                delay.milliseconds
              )
            } else {
              context.log.info(s"No next page token, chat may have ended for $liveChatId")
              // Chat may have ended or there's an error
            }
          } catch {
            case e: Exception =>
              context.log.error(s"Error processing response: ${e.getMessage}")
              
              // Handle retry with the same mechanism as network errors
              handleRetry(context, timers, liveChatId, currentPaginationToken, retryCount)
          }
          
          Behaviors.same
          
        case HandleError(error, liveChatId, paginationToken, retryCount) =>
          context.log.error(s"Error polling live chat $liveChatId: ${error.getMessage}")
          
          // Handle retry logic
          handleRetry(context, timers, liveChatId, paginationToken, retryCount)
          
          Behaviors.same
      }
    }
  }
  
  /**
   * Handle retry logic for API call failures
   */
  private def handleRetry(
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[Command],
    timers: TimerScheduler[Command],
    liveChatId: String,
    paginationToken: String,
    retryCount: Int
  ): Unit = {
    val maxRetries = 3
    val retryDelayMinutes = 3
    
    if (retryCount < maxRetries) {
      context.log.info(s"Scheduling retry ${retryCount + 1}/$maxRetries in $retryDelayMinutes minutes")
      timers.startSingleTimer(
        PollingTimer,
        PollLiveChat(liveChatId, paginationToken, retryCount + 1), // Increment retry count
        retryDelayMinutes.minutes
      )
    } else {
      context.log.error(s"Maximum retry attempts ($maxRetries) reached for $liveChatId. Stopping polling.")
      // Do not schedule another poll, effectively stopping the polling
    }
  }
  
  /**
   * Process a single message from the live chat
   */
  private def processMessage(
    message: JsValue, 
    liveChatId: String,
    ytStreamerRepository: YtStreamerRepository,
    userStreamerStateRepository: UserStreamerStateRepository,
    userRepository: UserRepository,
    ytUserRepository: YtUserRepository,
    startTime: Instant
  )(implicit ec: ExecutionContext): Unit = {
    try {
      // Extract message details
      val messageId = (message \ "id").as[String]
      val authorChannelId = (message \ "authorDetails" \ "channelId").as[String]
      val displayName = (message \ "authorDetails" \ "displayName").as[String]
      val messageText = (message \ "snippet" \ "displayMessage").as[String]
      
      // Extract the published time
      val publishedAtStr = (message \ "snippet" \ "publishedAt").as[String]
      val publishedAt = Instant.parse(publishedAtStr)
      
      // Only process messages that were published after we started monitoring
      if (publishedAt.isAfter(startTime)) {
        println(s"Processing message from $displayName published at $publishedAt")
        
        // Register the message author as a user if they don't already exist
        registerChatUser(authorChannelId, displayName, userRepository, ytUserRepository)
        
        // Check if this is a special message like a donation
        if (messageText.toLowerCase.contains("donation") || messageText.toLowerCase.contains("super chat")) {
          // Find the streamer by liveChatId and update balance
          // Note: In a real implementation, you'd need to map liveChatId to channelId
          // This is simplified for the example
          ytStreamerRepository.getByChannelId(liveChatId).flatMap {
            case Some(streamer) =>
              // Process the donation
              for {
                // Increment streamer balance
                _ <- ytStreamerRepository.incrementBalance(streamer.channelId, 1)
                
                // Increment user-streamer state balance
                _ <- userStreamerStateRepository.exists(0L, streamer.channelId).flatMap {
                  case true => userStreamerStateRepository.incrementBalance(0L, streamer.channelId, 1)
                  case false => userStreamerStateRepository.create(0L, streamer.channelId, 1).map(_ => 1)
                }
              } yield ()
              
            case None =>
              // Streamer not found, log error
              Future.successful(())
          }
        }
      } else {
        // Message is from before we started monitoring, so skip it
        println(s"Skipping message from $displayName published at $publishedAt (before $startTime)")
      }
    } catch {
      case e: Exception =>
        // Log error and continue
        println(s"Error processing message: ${e.getMessage}")
    }
  }
  
  /**
   * Register a new user from the chat message if they don't already exist
   *
   * @param channelId The YouTube channel ID of the user
   * @param displayName The display name shown in the chat
   * @param userRepository Repository for user operations
   * @param ytUserRepository Repository for YouTube user operations
   */
  private def registerChatUser(
    channelId: String, 
    displayName: String,
    userRepository: UserRepository,
    ytUserRepository: YtUserRepository
  )(implicit ec: ExecutionContext): Future[Option[YtUser]] = {
    // First, check if the YouTube user already exists
    ytUserRepository.getByChannelId(channelId).flatMap {
      case Some(existingUser) => 
        // User already exists, return it
        Future.successful(Some(existingUser))
      
      case None =>
        // User doesn't exist, create a new user and link it to a YouTube user
        println(s"Registering new user from chat: $displayName ($channelId)")
        for {
          // Create a new User with the display name as username
          newUser <- userRepository.create(displayName).map { user =>
            println(s"Created new user: ID=${user.userId}, Username=${user.userName}")
            user
          }
          
          // Create a new YtUser linked to the User
          ytUser <- ytUserRepository.createFull(YtUser(
            userChannelId = channelId,
            userId = newUser.userId,
            displayName = Some(displayName),
            email = None, // We don't have email from chat messages
            profileImageUrl = None, // We don't have profile image from chat messages
            activated = true // Automatically activate users from chat
          )).map { user =>
            println(s"Linked YouTube user: ChannelID=${user.userChannelId}, UserID=${user.userId}")
            user
          }
        } yield Some(ytUser)
    }
  }
}