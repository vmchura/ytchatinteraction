package services

import javax.inject.{Inject, Singleton}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.actor.typed.scaladsl.adapter._
import play.api.Logger
import play.api.libs.json.Json
import models.StreamerEvent

import java.util.UUID
import scala.concurrent.ExecutionContext

/**
 * Service for handling event updates via WebSocket
 */
@Singleton
class EventUpdateService @Inject()(
  implicit val system: org.apache.pekko.actor.typed.ActorSystem[Nothing],
  val ec: ExecutionContext,
  val mat: Materializer
) {
  private val logger = Logger(getClass)
  
  // Map to store client connections
  private var clients = Map.empty[String, ActorRef[String]]
  
  /**
   * Format an event update message for WebSocket
   */
  def formatEventUpdateMessage(event: StreamerEvent, action: String): String = {
    val messageJson = Json.obj(
      "type" -> "event_update",
      "action" -> action, // "new", "closed", etc.
      "eventId" -> event.eventId.getOrElse(-1),
      "eventName" -> event.eventName,
      "channelId" -> event.channelId,
      "eventDescription" -> event.eventDescription.getOrElse(""),
      "isActive" -> event.isActive,
      "timestamp" -> System.currentTimeMillis()
    )
    
    Json.stringify(messageJson)
  }
  
  /**
   * Broadcast event creation
   */
  def broadcastNewEvent(event: StreamerEvent): Unit = {
    val message = formatEventUpdateMessage(event, "new")
    broadcast(message)
  }
  
  /**
   * Broadcast event closure
   */
  def broadcastEventClosed(event: StreamerEvent): Unit = {
    val message = formatEventUpdateMessage(event, "closed")
    broadcast(message)
  }
  
  /**
   * Broadcast a message to all connected clients
   */
  private def broadcast(message: String): Unit = {
    clients.values.foreach { client =>
      client ! message
    }
  }
  
  /**
   * Register a new client connection
   */
  private def registerClient(id: String, actorRef: ActorRef[String]): Unit = {
    clients += (id -> actorRef)
    logger.info(s"Client $id connected. Total connected clients: ${clients.size}")
  }
  
  /**
   * Remove a client connection
   */
  def unregisterClient(id: String): Unit = {
    clients -= id
    logger.info(s"Client $id disconnected. Remaining clients: ${clients.size}")
  }
  
  /**
   * Creates a WebSocket flow for an event updates client
   */
  def eventsFlow(): Flow[String, String, _] = {
    // Generate a unique ID for this client
    val clientId = UUID.randomUUID().toString
    
    logger.info(s"Creating new events WebSocket connection for client: $clientId")

    // Create a sink that ignores incoming messages (we don't need client -> server for this)
    val incomingMessages: Sink[String, _] = Sink.ignore
    
    // Create a source that will emit messages to the client
    val outgoingMessages: Source[String, _] = Source.actorRef[String](
      // Completion strategies
      { case msg if msg == "COMPLETE" =>
        CompletionStrategy.immediately },
      { case msg if msg == "ERROR" =>
        throw new IllegalStateException("Already finished") },
      bufferSize = 100,
      OverflowStrategy.dropHead
    ).mapMaterializedValue { outgoingActor =>
      // Register this client
      registerClient(clientId, outgoingActor.toTyped[String])
      
      // Add a callback for when the stream completes
      system.classicSystem.registerOnTermination {
        unregisterClient(clientId)
      }
    }
    
    // Combine the sink and source into a flow
    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }
}
