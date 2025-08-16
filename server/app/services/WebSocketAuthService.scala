package services

import play.api.Logger
import play.api.mvc.RequestHeader

import java.net.URI
import javax.inject.*

@Singleton
class WebSocketAuthService @Inject()() {

  private val logger = Logger(getClass)

  private val allowedHosts = Set("localhost", "evolutioncomplete.com", "91.99.13.219")
  private val allowedPorts = Set(9000, 5000, 19001)

  def validateWebSocketRequest(implicit request: RequestHeader): Boolean = {
    sameOriginCheck
  }

  private def sameOriginCheck(implicit request: RequestHeader): Boolean = {
    request.headers.get("Origin") match {
      case Some(originValue) if originMatches(originValue) =>
        logger.debug(s"originCheck: originValue = $originValue")
        true
      case Some(badOrigin) =>
        logger.error(s"originCheck: rejecting request because Origin header value $badOrigin is not in the same origin")
        false
      case None =>
        logger.error("originCheck: rejecting request because no Origin header found")
        false
    }
  }

  private def originMatches(origin: String): Boolean = {
    try {
      val url = new URI(origin)
      allowedHosts.contains(url.getHost) && allowedPorts.contains(url.getPort)
    } catch {
      case _: Exception => 
        logger.error(s"Failed to parse origin URL: $origin")
        false
    }
  }
}
