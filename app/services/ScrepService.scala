package services

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}
import scala.concurrent.{ExecutionContext, Future}
import java.io.File
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData

case class ScrepResponse(success: Boolean, data: Option[JsValue], error: Option[String])
case class HealthResponse(status: String, timestamp: String, version: String)

object ScrepResponse {
  implicit val reads: Reads[ScrepResponse] = Json.reads[ScrepResponse]
  implicit val writes: Writes[ScrepResponse] = Json.writes[ScrepResponse]
}

object HealthResponse {
  implicit val reads: Reads[HealthResponse] = Json.reads[HealthResponse]
  implicit val writes: Writes[HealthResponse] = Json.writes[HealthResponse]
}

@Singleton
class ScrepService @Inject()(
  ws: WSClient,
  configuration: Configuration
)(implicit ec: ExecutionContext) {

  private val logger = Logger(this.getClass)
  
  // Configuration
  private val screpBaseUrl = configuration.get[String]("screp.base-url")
  private val screpTimeout = configuration.get[Int]("screp.timeout")
  
  /**
   * Check if SCREP service is healthy
   */
  def healthCheck(): Future[Boolean] = {
    val url = s"$screpBaseUrl/health"
    
    ws.url(url)
      .withRequestTimeout(scala.concurrent.duration.Duration(screpTimeout, "seconds"))
      .get()
      .map { response =>
        response.status == 200 && {
          val healthResponse = response.json.asOpt[HealthResponse]
          healthResponse.exists(_.status == "healthy")
        }
      }
      .recover {
        case ex =>
          logger.warn(s"SCREP health check failed: ${ex.getMessage}")
          false
      }
  }

  /**
   * Parse a StarCraft replay file
   */
  def parseReplay(
    replayFile: File,
    originalFilename: String,
    includeMap: Boolean = false,
    includeCommands: Boolean = false,
    includeComputed: Boolean = false
  ): Future[ScrepResponse] = {
    val url = s"$screpBaseUrl/api/parse"
    
    // Build query parameters
    val queryParams = Seq(
      if (includeMap) Some("map" -> "true") else None,
      if (includeCommands) Some("cmds" -> "true") else None,
      if (includeComputed) Some("computed" -> "true") else None
    ).flatten
    
    import play.api.libs.ws.ahc.AhcWSBodyWritables._
    import play.api.mvc.MultipartFormData
    
    val source = play.api.libs.streams.Streams.fileToBytes(replayFile)
    val filePart = MultipartFormData.FilePart("replay", originalFilename, Some("application/octet-stream"), source)
    val multipartForm = MultipartFormData(Map(), Seq(filePart), Seq())
    
    ws.url(url)
      .withQueryStringParameters(queryParams: _*)
      .withRequestTimeout(scala.concurrent.duration.Duration(screpTimeout, "seconds"))
      .post(multipartForm)
      .map { response =>
        if (response.status == 200) {
          response.json.asOpt[ScrepResponse].getOrElse(
            ScrepResponse(success = false, data = None, error = Some("Invalid response format"))
          )
        } else {
          ScrepResponse(
            success = false,
            data = None,
            error = Some(s"HTTP ${response.status}: ${response.body}")
          )
        }
      }
      .recover {
        case ex =>
          logger.error(s"Error parsing replay: ${ex.getMessage}", ex)
          ScrepResponse(success = false, data = None, error = Some(ex.getMessage))
      }
  }

  /**
   * Get overview of a StarCraft replay file
   */
  def getReplayOverview(replayFile: File, originalFilename: String): Future[ScrepResponse] = {
    val url = s"$screpBaseUrl/api/overview"
    
    import play.api.libs.ws.ahc.AhcWSBodyWritables._
    import play.api.mvc.MultipartFormData
    
    val source = play.api.libs.streams.Streams.fileToBytes(replayFile)
    val filePart = MultipartFormData.FilePart("replay", originalFilename, Some("application/octet-stream"), source)
    val multipartForm = MultipartFormData(Map(), Seq(filePart), Seq())
    
    ws.url(url)
      .withRequestTimeout(scala.concurrent.duration.Duration(screpTimeout, "seconds"))
      .post(multipartForm)
      .map { response =>
        if (response.status == 200) {
          response.json.asOpt[ScrepResponse].getOrElse(
            ScrepResponse(success = false, data = None, error = Some("Invalid response format"))
          )
        } else {
          ScrepResponse(
            success = false,
            data = None,
            error = Some(s"HTTP ${response.status}: ${response.body}")
          )
        }
      }
      .recover {
        case ex =>
          logger.error(s"Error getting replay overview: ${ex.getMessage}", ex)
          ScrepResponse(success = false, data = None, error = Some(ex.getMessage))
      }
  }

  /**
   * Parse replay from temporary file (from file upload)
   */
  def parseReplayFromUpload(
    tempFile: TemporaryFile,
    originalFilename: String,
    includeMap: Boolean = false,
    includeCommands: Boolean = false,
    includeComputed: Boolean = false
  ): Future[ScrepResponse] = {
    parseReplay(tempFile.file, originalFilename, includeMap, includeCommands, includeComputed)
  }

  /**
   * Get overview from temporary file (from file upload)
   */
  def getReplayOverviewFromUpload(
    tempFile: TemporaryFile,
    originalFilename: String
  ): Future[ScrepResponse] = {
    getReplayOverview(tempFile.file, originalFilename)
  }

  /**
   * Validate if a file is a valid StarCraft replay
   */
  def isValidReplayFile(filename: String): Boolean = {
    filename.toLowerCase.endsWith(".rep")
  }

  /**
   * Get service information
   */
  def getServiceInfo(): Future[JsValue] = {
    val url = screpBaseUrl
    
    ws.url(url)
      .withRequestTimeout(scala.concurrent.duration.Duration(screpTimeout, "seconds"))
      .get()
      .map(_.json)
      .recover {
        case ex =>
          logger.error(s"Error getting service info: ${ex.getMessage}", ex)
          Json.obj(
            "error" -> "Service unavailable",
            "message" -> ex.getMessage
          )
      }
  }
}
