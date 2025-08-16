package actors

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import play.api.Logger

/**
 * A typed actor that manages a chat room with multiple users connected via WebSockets
 * and handles broadcasting messages to all connected clients.
 */
object ChatRoomActor {
  private val logger = Logger(getClass)

  // Message protocol for the ChatRoomActor
  sealed trait Command

  // Command to register a new WebSocket client
  final case class Connect(clientId: String, client: ActorRef[String]) extends Command
  
  // Command to remove a WebSocket client when they disconnect
  final case class Disconnect(clientId: String) extends Command
  
  // Command to broadcast a message to all connected clients
  final case class Broadcast(message: String) extends Command
  
  // Internal message for when a client terminates
  private final case class ClientTerminated(clientId: String) extends Command

  // State to keep track of connected clients
  private case class RoomState(clients: Map[String, ActorRef[String]])

  /**
   * Create a new ChatRoomActor behavior
   * @return Behavior for the ChatRoomActor
   */
  def apply(): Behavior[Command] = 
    Behaviors.setup { context =>
      logger.info("ChatRoomActor started")
      chatRoom(RoomState(Map.empty))
    }

  /**
   * The main behavior of the ChatRoomActor
   * @param state Current state with connected clients
   * @return Updated behavior
   */
  private def chatRoom(state: RoomState): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case Connect(clientId, client) =>
          logger.info(s"Client connected: $clientId")
          
          // Watch the client for termination
          context.watchWith(client, ClientTerminated(clientId))
          
          // Add the client to our state
          chatRoom(state.copy(clients = state.clients + (clientId -> client)))

        case Disconnect(clientId) =>
          logger.info(s"Client disconnected: $clientId")
          
          // Remove the client from our state
          chatRoom(state.copy(clients = state.clients - clientId))

        case ClientTerminated(clientId) =>
          logger.info(s"Client terminated: $clientId")
          
          // Remove the terminated client
          chatRoom(state.copy(clients = state.clients - clientId))

        case Broadcast(message) =>
          logger.debug(s"Broadcasting message to ${state.clients.size} clients: $message")
          
          // Send the message to all connected clients
          state.clients.foreach { case (_, client) =>
            client ! message
          }
          
          Behaviors.same
      }
    }
  }
}