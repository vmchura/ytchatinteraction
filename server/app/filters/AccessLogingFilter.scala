package filters

import org.apache.pekko.stream.Materializer
import play.api.Logging
import play.api.mvc._
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory
import net.logstash.logback.argument.StructuredArguments._

@Singleton
class AccessLoggingFilter @Inject() (implicit
    val mat: Materializer,
    ec: ExecutionContext
) extends Filter
    with Logging {

  private val accessLogger = LoggerFactory.getLogger("access")

  def apply(
      nextFilter: RequestHeader => Future[Result]
  )(requestHeader: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis

    nextFilter(requestHeader).map { result =>
      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime

      accessLogger.info(
        "HTTP Request",
        keyValue("method", requestHeader.method),
        keyValue("path", requestHeader.path),
        keyValue("status", result.header.status),
        keyValue("duration_ms", requestTime),
        keyValue("remote_address", requestHeader.remoteAddress),
        keyValue(
          "user_agent",
          requestHeader.headers.get("User-Agent").getOrElse("unknown")
        ),
        keyValue("query_string", requestHeader.rawQueryString)
      )

      result
    }
  }
}
