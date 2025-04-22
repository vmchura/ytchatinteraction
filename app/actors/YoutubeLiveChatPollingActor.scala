package actors

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import play.api.libs.ws.WSClient
import play.api.libs.json.*
import models.*
import models.repository.{PollVoteRepository, UserRepository, UserStreamerStateRepository, YtStreamerRepository, YtUserRepository}
import services.{ChatService, InferUserOptionService, PollService}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

/**
 * Actor that handles polling the YouTube Live Chat API.
 * Uses Pekko Typed for actor implementation.
 */
object YoutubeLiveChatPollingActor {
  // Message protocol for the YoutubeLiveChatPollingActor
  sealed trait Command

  // Command to initiate or continue polling
  final case class PollLiveChat(liveChatId: String, channelID: String, paginationToken: String, retryCount: Int = 0) extends Command

  // Command to process the API response
  final case class ProcessApiResponse(response: JsValue, liveChatId: String, channelID: String, paginationToken: String, retryCount: Int) extends Command

  // Command to handle errors during API calls
  final case class HandleError(error: Throwable, liveChatId: String, channelID: String, paginationToken: String, retryCount: Int) extends Command

  // Command to process messages after getting poll data
  final case class ProcessMessages(
                                    messages: Seq[JsValue],
                                    liveChatId: String,
                                    channelID: String,
                                    startTime: Instant,
                                    pollEvent: (EventPoll, List[PollOption])
                                  ) extends Command

  // Command for when no poll is available
  final case class NoPollAvailable(liveChatId: String) extends Command

  // Timer key for scheduling
  private case object PollingTimer

  /**
   * Create a new YoutubeLiveChatPollingActor behavior
   */
  def apply(
             ws: WSClient,
             apiKey: String,
             ytStreamerRepository: YtStreamerRepository,
             userStreamerStateRepository: UserStreamerStateRepository,
             userRepository: UserRepository,
             ytUserRepository: YtUserRepository,
             startTime: Instant,
             pollService: PollService,
             inferUserOptionService: InferUserOptionService,
             chatService: ChatService,
             pollVoteRepository: PollVoteRepository
           ): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      active(ws, apiKey, ytStreamerRepository, userStreamerStateRepository, userRepository,
        ytUserRepository, startTime, timers, pollService, inferUserOptionService, chatService, pollVoteRepository)
    }
  }

  /**
   * Main behavior for handling chat polling
   */
  private def active(
                      ws: WSClient,
                      apiKey: String,
                      ytStreamerRepository: YtStreamerRepository,
                      userStreamerStateRepository: UserStreamerStateRepository,
                      userRepository: UserRepository,
                      ytUserRepository: YtUserRepository,
                      startTime: Instant,
                      timers: TimerScheduler[Command],
                      pollService: PollService,
                      inferUserOptionService: InferUserOptionService,
                      chatService: ChatService,
                      pollVoteRepository: PollVoteRepository
                    ): Behavior[Command] = {
    Behaviors.setup { context =>
      implicit val ec: ExecutionContext = context.executionContext

      Behaviors.receiveMessage {
        case PollLiveChat(liveChatId, channelID, paginationToken, retryCount) =>
          context.log.info(s"Polling live chat: $liveChatId, token: $paginationToken, retry: $retryCount")

          // Build the API URL
          val url = "https://www.googleapis.com/youtube/v3/liveChat/messages"

          // Build query parameters
          val queryParams = Map(
            "liveChatId" -> liveChatId,
            "part" -> "id,snippet,authorDetails",
            "key" -> apiKey
          ) ++ (if (paginationToken != null) Map("pageToken" -> paginationToken) else Map.empty)

          // Make the API call
          ws.url(url)
            .withQueryStringParameters(queryParams.toSeq: _*)
            .get()
            .map(response => ProcessApiResponse(response.json, liveChatId, channelID, paginationToken, retryCount))
            .recover { case error => HandleError(error, liveChatId, channelID, paginationToken, retryCount) }
            .foreach(context.self ! _)

          Behaviors.same

        case ProcessApiResponse(response, liveChatId, channelID, currentPaginationToken, retryCount) =>
          // Process the API response
          try {
            val nextPageToken = (response \ "nextPageToken").asOpt[String]
            val pollingIntervalMs = (response \ "pollingIntervalMillis").as[Int]

            // Process messages - only those published after our start time
            val items = (response \ "items").as[JsArray].value
            context.log.info(s"Received ${items.size} messages, filtering for those after ${startTime}")

            // Get the event poll, but handle the result in the actor context
            // Use pipeTo pattern to send the result back to self
            pollService.getPollForRecentEvent(channelID).map {
              case Some(eventPoll) => ProcessMessages(items.toSeq, liveChatId, channelID, startTime, eventPoll)
              case None => NoPollAvailable(liveChatId)
            }.recover {
              case ex =>
                // Log error but continue with next poll
                println(s"Error getting poll event: ${ex.getMessage}")
                NoPollAvailable(liveChatId)
            }.foreach(context.self ! _)

            // Schedule next poll based on YouTube's recommended interval
            if (nextPageToken.isDefined) {
              val delay = math.max(pollingIntervalMs, 60000) // Minimum 1 minute to avoid rate limits
              context.log.info(s"Scheduling next poll in ${delay}ms")

              // Reset retry count since we had a successful call
              timers.startSingleTimer(
                PollingTimer,
                PollLiveChat(liveChatId, channelID, nextPageToken.get, 0), // Reset retry count
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
              handleRetry(context, timers, liveChatId, channelID, currentPaginationToken, retryCount)
          }

          Behaviors.same

        case HandleError(error, liveChatId, channelID, paginationToken, retryCount) =>
          context.log.error(s"Error polling live chat $liveChatId: ${error.getMessage}")

          // Handle retry logic
          handleRetry(context, timers, liveChatId, channelID, paginationToken, retryCount)

          Behaviors.same

        case ProcessMessages(messages, liveChatId, channelID, messageStartTime, pollEvent) =>
          context.log.info(s"Processing ${messages.size} messages for live chat $liveChatId")
          println(s"Processing ${messages.size} messages for live chat $liveChatId")

          // Process each message - this happens within the actor context now
          messages.foreach { message =>
            println(s"Message $message")
            processMessageInActor(message, liveChatId, channelID, ytStreamerRepository, userStreamerStateRepository,
              userRepository, ytUserRepository, messageStartTime, pollEvent,
              inferUserOptionService, chatService, context, pollService)
          }

          Behaviors.same

        case NoPollAvailable(liveChatId) =>
          context.log.warn(s"No active poll available for live chat $liveChatId")
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
                           channelID: String,
                           paginationToken: String,
                           retryCount: Int
                         ): Unit = {
    val maxRetries = 3
    val retryDelayMinutes = 3

    if (retryCount < maxRetries) {
      context.log.info(s"Scheduling retry ${retryCount + 1}/$maxRetries in $retryDelayMinutes minutes")
      timers.startSingleTimer(
        PollingTimer,
        PollLiveChat(liveChatId, channelID, paginationToken, retryCount + 1), // Increment retry count
        retryDelayMinutes.minutes
      )
    } else {
      context.log.error(s"Maximum retry attempts ($maxRetries) reached for $liveChatId. Stopping polling.")
      // Do not schedule another poll, effectively stopping the polling
    }
  }

  /**
   * Process a single message from the live chat within the actor context
   * This is a safe version that won't try to access context from outside the actor
   */
  private def processMessageInActor(
                                     message: JsValue,
                                     liveChatId: String,
                                     channelID: String,
                                     ytStreamerRepository: YtStreamerRepository,
                                     userStreamerStateRepository: UserStreamerStateRepository,
                                     userRepository: UserRepository,
                                     ytUserRepository: YtUserRepository,
                                     startTime: Instant,
                                     pollEvent: (EventPoll, List[PollOption]),
                                     inferUserOptionService: InferUserOptionService,
                                     chatService: ChatService,
                                     context: ActorContext[Command],
                                     pollService: PollService
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
        context.log.info(s"Processing message from $displayName published at $publishedAt")
        println(s"Processing message from $displayName published at $publishedAt")

        // Register the message author as a user if they don't already exist
        val voteRegistered = for {
          user <- registerChatUser(authorChannelId, displayName, userRepository, ytUserRepository)
          relationExists <- userStreamerStateRepository.exists(user.userId, channelID)
          _ <- if (!relationExists) userStreamerStateRepository.create(user.userId, channelID, 1000) else Future.successful(())
          confidence <- inferUserOptionService.inferencePollResponse(
            eventPoll = pollEvent._1,
            options = pollEvent._2,
            response = messageText
          )
          voteRegistered <- confidence match {
            case Some((po, confidenceValue)) =>
              (for {
                pollID <- pollEvent._1.pollId
                optionID <- po.optionId
              } yield {
                println(s"$message : $po $confidenceValue")
                pollService.registerPollVote(pollID,
                  optionID,
                  user.userId,
                  Some(messageText),
                  confidenceValue).map(po => Some(po))
              }).getOrElse(Future.successful(None))

            case None =>
              println(s"$message : [None, None]")
              Future.successful(None)
          }

        } yield {
          voteRegistered
        }

        // Broadcast the message to all connected WebSocket clients
        // Format the message to show who sent it

        // Process the message for poll responses - don't use context logger here
        voteRegistered.onComplete {
          case Success(Some(pollVote)) =>
            // Successfully processed poll response, but don't log from here
            chatService.broadcastMessage(s"$displayName: [${pollVote.pollId}]+${pollVote.pollQuestion} ", "youtube", Some(displayName))
          case Success(None) =>
            // No poll response detected, but don't log from here
            chatService.broadcastMessage(s"$displayName: $messageText: [  ]", "youtube", Some(displayName))
          case Failure(ex) =>
            // Just print to console to avoid actor context issues
            println(s"Error processing poll response: ${ex.getMessage}")
        }
      } else {
        // Message is from before we started monitoring, so skip it
        context.log.debug(s"Skipping message from $displayName published at $publishedAt (before $startTime)")
      }
    } catch {
      case e: Exception =>
        // Log error and continue
        context.log.error(s"Error processing message: ${e.getMessage}")
    }
  }

  /**
   * Register a new user from the chat message if they don't already exist
   */
  private def registerChatUser(
                                channelId: String,
                                displayName: String,
                                userRepository: UserRepository,
                                ytUserRepository: YtUserRepository
                              )(implicit ec: ExecutionContext): Future[YtUser] = {
    // First, check if the YouTube user already exists
    ytUserRepository.getByChannelId(channelId).flatMap {
      case Some(existingUser) =>
        // User already exists, return it
        Future.successful(existingUser)

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
        } yield ytUser
    }
  }
}