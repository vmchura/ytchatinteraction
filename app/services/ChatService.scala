package services

import actors.ChatRoomActor
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}

import javax.inject.{Inject, Singleton}
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.stream.{CompletionStrategy, Materializer, OverflowStrategy, Supervision}
import org.apache.pekko.actor.typed.scaladsl.adapter._

import java.util.UUID
import scala.concurrent.ExecutionContext

/**
 * Service for managing chat functionality and WebSocket connections.
 * Uses Pekko Typed actors for managing the chat room.
 */
@Singleton
class ChatService @Inject()(
  implicit val system: org.apache.pekko.actor.typed.ActorSystem[Nothing],
  val ec: ExecutionContext,
  val mat: Materializer
) {
  private val logger = Logger(getClass)
  
  // Create a single ChatRoomActor to manage all connections
  private val chatRoomActor: ActorRef[ChatRoomActor.Command] = 
    system.systemActorOf(ChatRoomActor(), "chat-room")
  
  /**
   * Format a raw message into JSON for the chat
   * 
   * @param message The message text
   * @param source The source of the message (user, youtube, system, etc.)
   * @param userName Optional username
   * @return JSON formatted message
   */
  def formatChatMessage(message: String, source: String, userName: Option[String] = None): String = {
    val messageJson = Json.obj(
      "message" -> message,
      "source" -> source,
      "timestamp" -> System.currentTimeMillis(),
      "userName" -> userName.getOrElse("System")
    )
    
    Json.stringify(messageJson)
  }

  def formatVoteMessage(
                         userName: String,
                         message: String,
                         optionText: Option[String],
                         confidence: Option[Int],
                         eventId: Int
                       ): String = {
    val messageJson = Json.obj(
      "type" -> "vote_detection",
      "userName" -> userName,
      "message" -> message,
      "optionText" -> optionText.getOrElse("No option"),
      "confidence" -> confidence.map(_.toString).getOrElse(""),
      "eventId" -> eventId,
      "timestamp" -> System.currentTimeMillis()
    )

    Json.stringify(messageJson)
  }

  def broadcastVoteDetection(
                              userName: String,
                              message: String,
                              optionText: Option[String],
                              confidence: Option[Int],
                              eventId: Int
                            ): Unit = {
    val formattedMessage = formatVoteMessage(userName, message, optionText, confidence, eventId)
    chatRoomActor ! ChatRoomActor.Broadcast(formattedMessage)
  }
  
  /**
   * Broadcast a message to all connected clients
   * 
   * @param message The message text to broadcast
   * @param source The source of the message (youtube, system, etc.)
   * @param userName Optional username
   */
  def broadcastMessage(message: String, source: String, userName: Option[String] = None): Unit = {
    val formattedMessage = formatChatMessage(message, source, userName)
    chatRoomActor ! ChatRoomActor.Broadcast(formattedMessage)
  }
  
  /**
   * Creates a WebSocket flow for a client
   * 
   * @return A flow that handles the WebSocket communication
   */
  def chatFlow(): Flow[String, String, _] = {
    // Generate a unique ID for this client
    val clientId = UUID.randomUUID().toString
    
    logger.info(s"Creating new WebSocket connection for client: $clientId")

    // Create a sink that forwards incoming messages to the actor
    val incomingMessages: Sink[String, _] = Sink.foreach[String] { message =>
      // When we receive a message from the client, broadcast it to everyone
      broadcastMessage(message, "user", Some(clientId))
    }
    
    // Create a source that will emit messages to the client
    val outgoingMessages: Source[String, _] = Source.actorRef[String](
      // Set proper completion strategies
      { case msg if msg == "COMPLETE" =>
        CompletionStrategy.immediately },  // Only complete on this specific message
      { case msg if msg == "ERROR" =>
        throw new IllegalStateException("Already finished")
      }, // Don't fail on other messages, just resume
      bufferSize = 100,
      OverflowStrategy.dropHead
    ).mapMaterializedValue { outgoingActor =>
      // Register this client with the chat room
      chatRoomActor ! ChatRoomActor.Connect(clientId, outgoingActor.toTyped[String])
      
      // Add a callback for when the stream completes
      system.classicSystem.registerOnTermination {
        chatRoomActor ! ChatRoomActor.Disconnect(clientId)
      }
    }
    
    // Combine the sink and source into a flow
    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }
}