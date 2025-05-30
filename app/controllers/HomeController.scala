package controllers

import java.net.URI
import javax.inject.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.{Logging, LoggingAdapter}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Source}
import play.api.Logger
import play.api.mvc.*
import play.api.i18n.I18nSupport
import services.ChatService

import scala.concurrent.{ExecutionContext, Future}

/**
 * A very simple chat client using websockets.
 */
@Singleton
class HomeController @Inject()(val scc: SilhouetteControllerComponents,
                               inputSanitizer: InputSanitizer,
                               chatService: ChatService)
                              (implicit actorSystem: ActorSystem,
                               mat: Materializer,
                               executionContext: ExecutionContext,
                               webJarsUtil: org.webjars.play.WebJarsUtil)
  extends SilhouetteController(scc) with RequestMarkerContext {

  private type WSMessage = String

  private val logger = Logger(getClass)

  private implicit val logging: LoggingAdapter = Logging(actorSystem.eventStream, logger.underlyingLogger.getName)

  // We're now using ChatService for WebSocket management instead of MergeHub/BroadcastHub

  def index(): Action[AnyContent] = silhouette.UserAwareAction { implicit request =>
    val webSocketUrl = routes.HomeController.streamerevents().webSocketURL()
    request.identity match {
      case Some(user) =>
        // User is logged in, show dashboard with user information
        Redirect(routes.UserEventsController.userEvents())
      case None =>
        // User is not logged in, show welcome/landing page
        Ok(views.html.welcome())
    }
  }

  def streamerevents(): WebSocket = {
    WebSocket.acceptOrResult[WSMessage, WSMessage] {
      case rh if sameOriginCheck(rh) =>
        // Use the chatFlow from ChatService instead of userFlow
        Future.successful(Right(chatService.chatFlow()))
          .recover {
            case e: Exception =>
              val msg = "Cannot create websocket"
              logger.error(msg, e)
              val result = InternalServerError(msg)
              Left(result)
          }

      case rejected =>
        logger.error(s"Request ${rejected} failed same origin check")
        Future.successful {
          Left(Forbidden("forbidden"))
        }
    }
  }

  /**
   * Checks that the WebSocket comes from the same origin.  This is necessary to protect
   * against Cross-Site WebSocket Hijacking as WebSocket does not implement Same Origin Policy.
   *
   * See https://tools.ietf.org/html/rfc6455#section-1.3 and
   * http://blog.dewhurstsecurity.com/2013/08/30/security-testing-html5-websockets.html
   */
  private def sameOriginCheck(implicit rh: RequestHeader): Boolean = {
    // The Origin header is the domain the request originates from.
    // https://tools.ietf.org/html/rfc6454#section-7
    logger.debug("Checking the ORIGIN ")

    rh.headers.get("Origin") match {
      case Some(originValue) if originMatches(originValue) =>
        logger.debug(s"originCheck: originValue = $originValue")
        true

      case Some(badOrigin) =>
        logger.error(s"originCheck: rejecting request because Origin header value ${badOrigin} is not in the same origin")
        false

      case None =>
        logger.error("originCheck: rejecting request because no Origin header found")
        false
    }
  }

  /**
   * Returns true if the value of the Origin header contains an acceptable value.
   */
  private def originMatches(origin: String): Boolean = {
    try {
      val url = new URI(origin)
      (url.getHost == "localhost" || url.getHost == "evolutioncomplete.com" || url.getHost == "91.99.13.219") &&
        (url.getPort match { case 9000 | 5000 | 19001 => true; case _ => false })
    } catch {
      case e: Exception => false
    }
  }
}