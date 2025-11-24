package controllers

import javax.inject.*
import play.api.Logger
import play.api.mvc.*
import play.api.libs.ws.WSClient
import play.api.Configuration
import play.api.i18n.I18nSupport
import utils.auth.WithAdmin

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files, Paths}
import java.time.Instant

case class HealthStatus(
                         service: String,
                         status: String,
                         reachable: Boolean,
                         responseTime: Option[Long] = None,
                         error: Option[String] = None
                       )

case class StorageHealthStatus(
                                path: String,
                                exists: Boolean,
                                readable: Boolean,
                                writable: Boolean,
                                freeSpaceGB: Option[Long] = None,
                                totalSpaceGB: Option[Long] = None,
                                fileCount: Option[Int] = None,
                                status: String,
                                error: Option[String] = None,
                                checkedAt: String
                              )

@Singleton
class ReplayParserHealthController @Inject()(
                                              components: DefaultSilhouetteControllerComponents,
                                              ws: WSClient,
                                              configuration: Configuration
                                            )(implicit ec: ExecutionContext) extends SilhouetteController(components) with I18nSupport with RequestMarkerContext {

  private val logger = Logger(getClass)

  private val replayParserUrl = configuration.get[String]("replayparser.url")
  private val replayAnalyticalUrl = configuration.get[String]("replayanalytical.url")

  private val uploadStoragePath = configuration.get[String]("app.storage.uploads.path")

  def healthPage(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    logger.info("Performing health checks for page rendering")

    val parserHealthFuture = checkParserHealthOnce()
    val analyticalHealthFuture = checkAnalyticalHealthOnce()
    val storageHealthFuture = Future.successful(checkStorageHealthSync())

    for {
      parserHealth <- parserHealthFuture
      analyticalHealth <- analyticalHealthFuture
      storageHealth <- storageHealthFuture
    } yield {
      logger.info(s"Health check complete - Parser: ${parserHealth.status}, Analytical: ${analyticalHealth.status}, Storage: ${storageHealth.status}")
      Ok(views.html.replayParserHealth(None, Some(parserHealth), Some(analyticalHealth), Some(storageHealth)))
    }
  }

  private def checkStorageHealthSync(): StorageHealthStatus = {
    try {
      val path = Paths.get(uploadStoragePath)
      val exists = Files.exists(path)

      if (!exists) {
        StorageHealthStatus(
          path = uploadStoragePath,
          exists = false,
          readable = false,
          writable = false,
          status = "unhealthy",
          error = Some("Storage directory does not exist"),
          checkedAt = Instant.now().toString
        )
      } else {
        val readable = Files.isReadable(path)
        val writable = Files.isWritable(path)

        val (freeSpace, totalSpace) = Try {
          val store = Files.getFileStore(path)
          (Some(store.getUsableSpace / (1024 * 1024 * 1024)), // Convert to GB
            Some(store.getTotalSpace / (1024 * 1024 * 1024)))
        }.getOrElse((None, None))

        val fileCount = Try {
          Files.list(path).iterator().asScala.length
        }.toOption

        val status = if (readable && writable) "healthy" else "unhealthy"
        val error = if (!readable && !writable) Some("Directory not readable or writable")
        else if (!readable) Some("Directory not readable")
        else if (!writable) Some("Directory not writable")
        else None

        StorageHealthStatus(
          path = uploadStoragePath,
          exists = true,
          readable = readable,
          writable = writable,
          freeSpaceGB = freeSpace,
          totalSpaceGB = totalSpace,
          fileCount = fileCount,
          status = status,
          error = error,
          checkedAt = Instant.now().toString
        )
      }
    } catch {
      case ex: Exception =>
        logger.error(s"Storage health check failed: ${ex.getMessage}", ex)
        StorageHealthStatus(
          path = uploadStoragePath,
          exists = false,
          readable = false,
          writable = false,
          status = "error",
          error = Some(s"Health check failed: ${ex.getMessage}"),
          checkedAt = Instant.now().toString
        )
    }
  }

  private def checkParserHealthOnce(): Future[HealthStatus] = {
    val startTime = System.currentTimeMillis()

    logger.info(s"Checking health of replay-parser service at $replayParserUrl")

    ws.url(s"$replayParserUrl/health")
      .withRequestTimeout(scala.concurrent.duration.Duration(10, "seconds"))
      .get()
      .map { response =>
        val responseTime = System.currentTimeMillis() - startTime

        if (response.status == 200) {
          logger.info(s"Replay-parser service is healthy (${responseTime}ms)")
          HealthStatus(
            service = "replay-parser",
            status = "healthy",
            reachable = true,
            responseTime = Some(responseTime)
          )
        } else {
          logger.warn(s"Replay-parser service returned status ${response.status}")
          HealthStatus(
            service = "replay-parser",
            status = "unhealthy",
            reachable = true,
            responseTime = Some(responseTime),
            error = Some(s"HTTP ${response.status}")
          )
        }
      }
      .recover {
        case ex =>
          val responseTime = System.currentTimeMillis() - startTime
          logger.error(s"Failed to reach replay-parser service: ${ex.getMessage}", ex)
          HealthStatus(
            service = "replay-parser",
            status = "unreachable",
            reachable = false,
            responseTime = Some(responseTime),
            error = Some(ex.getMessage)
          )
      }
  }

  private def checkAnalyticalHealthOnce(): Future[HealthStatus] = {
    val startTime = System.currentTimeMillis()

    logger.info(s"Checking health of replay-analytical service at $replayAnalyticalUrl")

    ws.url(s"$replayAnalyticalUrl/health")
      .withRequestTimeout(scala.concurrent.duration.Duration(10, "seconds"))
      .get()
      .map { response =>
        val responseTime = System.currentTimeMillis() - startTime

        if (response.status == 200) {
          logger.info(s"Replay-analytical service is healthy (${responseTime}ms)")
          HealthStatus(
            service = "replay-analytical",
            status = "healthy",
            reachable = true,
            responseTime = Some(responseTime)
          )
        } else {
          logger.warn(s"Replay-analytical service returned status ${response.status}")
          HealthStatus(
            service = "replay-analytical",
            status = "unhealthy",
            reachable = true,
            responseTime = Some(responseTime),
            error = Some(s"HTTP ${response.status}")
          )
        }
      }
      .recover {
        case ex =>
          val responseTime = System.currentTimeMillis() - startTime
          logger.error(s"Failed to reach replay-analytical service: ${ex.getMessage}", ex)
          HealthStatus(
            service = "replay-analytical",
            status = "unreachable",
            reachable = false,
            responseTime = Some(responseTime),
            error = Some(ex.getMessage)
          )
      }
  }
}