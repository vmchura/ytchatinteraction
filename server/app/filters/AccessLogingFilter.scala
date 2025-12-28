package filters

import org.apache.pekko.stream.Materializer
import play.api.Logging
import play.api.mvc._
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory
import net.logstash.logback.argument.StructuredArguments._
import java.util.UUID
import services.IpSecurityService

@Singleton
class AccessLoggingFilter @Inject() (
    ipSecurityService: IpSecurityService
)(implicit
    val mat: Materializer,
    ec: ExecutionContext
) extends Filter
    with Logging {

  private val accessLogger = LoggerFactory.getLogger("access")
  private val sessionIdKey = "sid"

  private def getClientIp(requestHeader: RequestHeader): String = {
    requestHeader.headers
      .get("X-Forwarded-For")
      .flatMap(_.split(",").headOption.map(_.trim))
      .orElse(requestHeader.headers.get("X-Real-IP"))
      .getOrElse(requestHeader.remoteAddress)
  }

  def apply(
      nextFilter: RequestHeader => Future[Result]
  )(requestHeader: RequestHeader): Future[Result] = {
    val clientIp = getClientIp(requestHeader)

    if (ipSecurityService.isBlocked(clientIp)) {
      Future.successful(Results.Forbidden("Access denied"))
    } else {
      val startTime = System.currentTimeMillis
      val sessionId = requestHeader.session
        .get(sessionIdKey)
        .getOrElse(UUID.randomUUID().toString)
      val userId = requestHeader.session.get("userId").getOrElse("guest")

      nextFilter(requestHeader).map { result =>
        val endTime = System.currentTimeMillis
        val requestTime = endTime - startTime

        ipSecurityService.recordRequest(
          clientIp,
          requestHeader.path,
          result.header.status
        )

        if (!requestHeader.path.contains("/assets")) {
          accessLogger.info(
            "HTTP Request",
            keyValue("method", requestHeader.method),
            keyValue("path", requestHeader.path),
            keyValue("status", result.header.status),
            keyValue("duration_ms", requestTime),
            keyValue("client_ip", clientIp),
            keyValue(
              "user_agent",
              requestHeader.headers.get("User-Agent").getOrElse("unknown")
            ),
            keyValue("query_string", requestHeader.rawQueryString),
            keyValue("session_id", sessionId),
            keyValue("user_id", userId)
          )
        }

        val currentSession = requestHeader.session.get(sessionIdKey)
        if (currentSession.isEmpty) {
          result.withSession(
            requestHeader.session + (sessionIdKey -> sessionId)
          )
        } else {
          result
        }
      }
    }
  }

}
