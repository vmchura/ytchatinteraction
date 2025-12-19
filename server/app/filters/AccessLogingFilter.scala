package filters

import org.apache.pekko.stream.Materializer
import play.api.Logging
import play.api.mvc._
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory
import net.logstash.logback.argument.StructuredArguments._
import java.util.UUID

@Singleton
class AccessLoggingFilter @Inject() (implicit
    val mat: Materializer,
    ec: ExecutionContext
) extends Filter
    with Logging {

  private val accessLogger = LoggerFactory.getLogger("access")
  private val sessionIdKey = "sid"

  def apply(
      nextFilter: RequestHeader => Future[Result]
  )(requestHeader: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis
    val sessionId = requestHeader.session
      .get(sessionIdKey)
      .getOrElse(UUID.randomUUID().toString)
    val userId = requestHeader.session.get("userId").getOrElse("guest")

    nextFilter(requestHeader).map {
      result =>
        val endTime = System.currentTimeMillis
        val requestTime = endTime - startTime

        if (!requestHeader.path.contains("/assets")) {
          accessLogger.info(
            "HTTP Request",
            keyValue("method", requestHeader.method),
            keyValue("path", requestHeader.path),
            keyValue("status", result.header.status),
            keyValue("duration_ms", requestTime),
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
