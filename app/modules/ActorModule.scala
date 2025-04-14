package modules

import actors.RootBehavior
import com.google.inject.{AbstractModule, TypeLiteral}
import javax.inject.{Inject, Provider, Singleton}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import play.api.Configuration
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

/**
 * Module to provide a properly configured Pekko ActorSystem
 */
class ActorModule extends AbstractModule {
  override def configure(): Unit = {
    // Use a TypeLiteral to specify the exact type with generic parameter
    bind(new TypeLiteral[ActorSystem[Nothing]]() {})
      .toProvider(classOf[ActorSystemProvider])
  }
}

/**
 * Provider for typed ActorSystem
 */
@Singleton
class ActorSystemProvider @Inject()(
  lifecycle: ApplicationLifecycle,
  configuration: Configuration
) extends Provider[ActorSystem[Nothing]] {
  
  // Create the ActorSystem with the root behavior
  private val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.empty,
    "application",
    configuration.underlying
  )
  
  lifecycle.addStopHook { () =>
    Future.successful(system.terminate())
  }
  
  override def get(): ActorSystem[Nothing] = system
}
