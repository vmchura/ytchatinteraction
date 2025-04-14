package actors

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors

/**
 * The root actor that supervises all child actors in our application
 */
object RootBehavior {
  def apply(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      // Create any top-level actors here
      
      // No messages handled by the root actor
      Behaviors.empty
    }
  }
}
