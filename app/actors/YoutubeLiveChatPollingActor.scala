package actors

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import play.api.libs.ws.WSClient
import play.api.libs.json.*
import models.*
import models.repository.{PollVoteRepository, UserRepository, UserStreamerStateRepository, YoutubeChatMessageRepository, YtStreamerRepository, YtUserRepository}
import services.{ActiveLiveStream, ChatService, InferUserOptionService, PollService}

import java.time.Instant
import java.time.format.DateTimeFormatter
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

  // Command to process messages when no poll is available
  final case class ProcessMessagesWithoutPoll(
                                               messages: Seq[JsValue],
                                               liveChatId: String,
                                               channelID: String,
                                               startTime: Instant
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
             pollVoteRepository: PollVoteRepository,
             liveStream: ActiveLiveStream,
             youtubeChatMessageRepository: YoutubeChatMessageRepository
           ): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      active(ws, apiKey, ytStreamerRepository, userStreamerStateRepository, userRepository,
        ytUserRepository, startTime, timers, pollService, inferUserOptionService, chatService, pollVoteRepository, liveStream, youtubeChatMessageRepository)
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
                      pollVoteRepository: PollVoteRepository,
                      liveStream: ActiveLiveStream,
                      youtubeChatMessageRepository: YoutubeChatMessageRepository
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
            
            // Save chat messages to PostgreSQL
            val chatMessages = items.map { item =>
              try {
                val messageId = (item \ "id").as[String]
                val authorChannelId = (item \ "authorDetails" \ "channelId").as[String]
                val displayName = (item \ "authorDetails" \ "displayName").as[String]
                val messageText = (item \ "snippet" \ "displayMessage").as[String]
                val publishedAtStr = (item \ "snippet" \ "publishedAt").as[String]
                val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                val publishedAt = Instant.from(formatter.parse(publishedAtStr))

                YoutubeChatMessage(
                  messageId = None, // Auto-generated
                  liveChatId = liveChatId,
                  channelId = channelID,
                  rawMessage = item.toString, // Store the entire JSON
                  authorChannelId = authorChannelId,
                  authorDisplayName = displayName,
                  messageText = messageText,
                  publishedAt = publishedAt
                )
              } catch {
                case e: Exception =>
                  context.log.error(s"Error parsing message: ${e.getMessage}")
                  null
              }
            }.filterNot(_ == null)

            // Store messages in batches
            if (chatMessages.nonEmpty) {
              youtubeChatMessageRepository.createBatch(chatMessages.toSeq).onComplete {
                case Success(_) => println(s"Successfully saved ${chatMessages.size} chat messages to PostgreSQL")
                case Failure(ex) => println(s"Failed to save chat messages: ${ex.getMessage}")
              }
            }
            val touchUser = chatMessages.foldLeft[Future[List[YtUser]]](Future.successful(List.empty)) {
              case (accFuture, singleMessage) =>
                for {
                  acc <- accFuture
                  registeredUser <- registerChatUser(singleMessage.authorChannelId, singleMessage.authorDisplayName, userRepository, ytUserRepository)
                  relationExists <- userStreamerStateRepository.exists(registeredUser.userId, channelID)
                  _ <- if (!relationExists) userStreamerStateRepository.create(registeredUser.userId, channelID, 0) else Future.successful(())
                } yield acc :+ registeredUser
            }


            // Get the event poll, but handle the result in the actor context
            // Use pipeTo pattern to send the result back to self
            val commandByMessage = for {
              _ <- touchUser
              recentPoll <- pollService.getPollForRecentEvent(channelID)
            }yield{
              recentPoll match {
                case Some(eventPoll) => ProcessMessages(items.toSeq, liveChatId, channelID, startTime, eventPoll)
                case None => 
                  // No poll available, but still process messages for broadcasting
                  ProcessMessagesWithoutPoll(items.toSeq, liveChatId, channelID, startTime)
              }
            }

            commandByMessage.recover {
              case ex =>
                ProcessMessagesWithoutPoll(items.toSeq, liveChatId, channelID, startTime)
            }.foreach(context.self ! _)

            // Schedule next poll based on YouTube's recommended interval
            if (nextPageToken.isDefined) {
              val delay = math.max(pollingIntervalMs, 30000) // Minimum 1 minute to avoid rate limits
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
              handleRetry(context, timers, liveChatId, channelID, currentPaginationToken, retryCount, liveStream)
          }

          Behaviors.same

        case HandleError(error, liveChatId, channelID, paginationToken, retryCount) =>
          context.log.error(s"Error polling live chat $liveChatId: ${error.getMessage}")

          // Handle retry logic
          handleRetry(context, timers, liveChatId, channelID, paginationToken, retryCount, liveStream)

          Behaviors.same

        case ProcessMessages(messages, liveChatId, channelID, messageStartTime, pollEvent) =>
          println(s"Processing ${messages.size} messages for live chat $liveChatId")

          // Process each message - this happens within the actor context now
          Future.sequence(
          messages.map { message =>
            val f = processMessageInActor(message, liveChatId, channelID, ytStreamerRepository, userStreamerStateRepository,
              userRepository, ytUserRepository, messageStartTime, pollEvent,
              inferUserOptionService, chatService, context, pollService)
            f.onComplete{
              case Success(u) => println(s"Saved $u")
              case Failure(ex) => println(s"Error saving $message: ${ex.getMessage}")
            }
            f
          }).map(_.foreach(println))

          Behaviors.same

        case ProcessMessagesWithoutPoll(messages, liveChatId, channelID, messageStartTime) =>
          println(s"Processing ${messages.size} messages without poll for live chat $liveChatId")

          // Process each message without poll functionality - just broadcast them
          Future.sequence(
            messages.map { message =>
              val f = processMessageWithoutPoll(message, liveChatId, channelID, userRepository, ytUserRepository,
                userStreamerStateRepository, messageStartTime, chatService, context)
              f.onComplete{
                case Success(u) => println(s"Broadcasted message: $u")
                case Failure(ex) => println(s"Error broadcasting message: ${ex.getMessage}")
              }
              f
            }
          )

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
                           retryCount: Int,
                           liveStream: ActiveLiveStream
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

      liveStream.removeElement(liveChatId)
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
                                   )(implicit ec: ExecutionContext): Future[Unit] = {
    try {
      // Extract message details
      val messageId = (message \ "id").as[String]
      val authorChannelId = (message \ "authorDetails" \ "channelId").as[String]
      val displayName = (message \ "authorDetails" \ "displayName").as[String]
      val messageText = (message \ "snippet" \ "displayMessage").as[String]

      // Extract the published time
      val publishedAtStr = (message \ "snippet" \ "publishedAt").as[String]
      val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val publishedAt = Instant.from(formatter.parse(publishedAtStr))

      // Only process messages that were published after we started monitoring
      if (publishedAt.isAfter(startTime)) {
        context.log.info(s"Processing message from $displayName published at $publishedAt")

        // Register the message author as a user if they don't already exist
        val voteRegistered = for {
          user <- registerChatUser(authorChannelId, displayName, userRepository, ytUserRepository)
          relationExists <- userStreamerStateRepository.exists(user.userId, channelID)
          _ <- if (!relationExists) userStreamerStateRepository.create(user.userId, channelID, 0) else Future.successful(())
          confidence <- inferUserOptionService.inferencePollResponse(
            eventPoll = pollEvent._1,
            options = pollEvent._2,
            response = messageText
          )
          voteRegistered <- confidence match {
            case Some((po, confidenceValue)) =>
              println(s"$po: $confidenceValue")
              (for {
                pollID <- pollEvent._1.pollId
                optionID <- po.optionId
              } yield {
                pollService.registerPollVote(pollID,
                  optionID,
                  user.userId,
                  Some(messageText),
                  confidenceValue, channelID).map(e =>{
                  println(s"Register vote: $e")
                  Some((po, confidenceValue))})
              }).getOrElse(Future.successful(None))

            case None =>
              Future.successful(None)
          }

        } yield {
          voteRegistered
        }

        // Broadcast the message to all connected WebSocket clients
        // Format the message to show who sent it
        voteRegistered.foreach { voteResult =>
          voteResult match {
            case Some((pollOption, confidence)) =>
              // Successfully processed poll response
              val optionText = pollOption.optionText
              val eventId = pollEvent._1.eventId
              chatService.broadcastVoteDetection(displayName, messageText, Some(optionText), Some(confidence), eventId)
            case None =>
              // No poll response detected, still broadcast the message
              chatService.broadcastMessage(s"[  ] $messageText", "youtube", Some(displayName))
          }
        }

        // Process the message for poll responses - don't use context logger here
        voteRegistered.onComplete {
          case Success(Some(pollVote)) =>
            // Successfully processed poll response, but don't log from here
            println(pollVote)
          case Success(None) =>
            // No poll response detected, but don't log from here
            println("Success but None")
          case Failure(ex) =>
            // Just print to console to avoid actor context issues
            println(s"Error processing poll response: ${ex.getMessage}")
        }
        voteRegistered.map(_ => ())
      } else {
        // Message is from before we started monitoring, so skip it
        context.log.debug(s"Skipping message from $displayName published at $publishedAt (before $startTime)")
        Future.successful(())
      }
    } catch {
      case e: Exception =>
        // Log error and continue
        context.log.error(s"Error processing message: ${e.getMessage}")
        Future.successful(())
    }
  }

  /**
   * Process a single message from the live chat when there's no poll available
   */
  private def processMessageWithoutPoll(
                                         message: JsValue,
                                         liveChatId: String,
                                         channelID: String,
                                         userRepository: UserRepository,
                                         ytUserRepository: YtUserRepository,
                                         userStreamerStateRepository: UserStreamerStateRepository,
                                         startTime: Instant,
                                         chatService: ChatService,
                                         context: ActorContext[Command]
                                       )(implicit ec: ExecutionContext): Future[Unit] = {
    try {
      // Extract message details
      val messageId = (message \ "id").as[String]
      val authorChannelId = (message \ "authorDetails" \ "channelId").as[String]
      val displayName = (message \ "authorDetails" \ "displayName").as[String]
      val messageText = (message \ "snippet" \ "displayMessage").as[String]

      // Extract the published time
      val publishedAtStr = (message \ "snippet" \ "publishedAt").as[String]
      val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
      val publishedAt = Instant.from(formatter.parse(publishedAtStr))

      // Only process messages that were published after we started monitoring
      if (publishedAt.isAfter(startTime)) {
        context.log.info(s"Processing message from $displayName published at $publishedAt (no poll)")

        // Register the message author as a user if they don't already exist
        val messageProcessed = for {
          user <- registerChatUser(authorChannelId, displayName, userRepository, ytUserRepository)
          relationExists <- userStreamerStateRepository.exists(user.userId, channelID)
          _ <- if (!relationExists) userStreamerStateRepository.create(user.userId, channelID, 0) else Future.successful(())
        } yield {
          // Broadcast the message without poll information
          chatService.broadcastMessage(s"[  ] $messageText", "youtube", Some(displayName))
          ()
        }

        messageProcessed
      } else {
        // Message is from before we started monitoring, so skip it
        context.log.debug(s"Skipping message from $displayName published at $publishedAt (before $startTime)")
        Future.successful(())
      }
    } catch {
      case e: Exception =>
        // Log error and continue
        context.log.error(s"Error processing message without poll: ${e.getMessage}")
        Future.successful(())
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
        for {
          // Create a new User with the display name as username
          newUser <- userRepository.create(displayName).map { user =>
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
            user
          }
        } yield ytUser
    }
  }
}