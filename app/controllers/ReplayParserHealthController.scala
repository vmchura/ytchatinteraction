package controllers

import javax.inject.*
import play.api.Logger
import play.api.mvc.*
import play.api.libs.ws.WSClient
import play.api.libs.ws.JsonBodyWritables.*
import play.api.libs.json.{JsValue, Json, OWrites}
import play.api.Configuration

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters.*
import java.nio.file.{Files, Path, Paths}
import java.io.File
import java.time.Instant

case class HealthStatus(
  service: String,
  status: String,
  reachable: Boolean,
  responseTime: Option[Long] = None,
  error: Option[String] = None
)

object HealthStatus {
  implicit val healthStatusWrites: OWrites[HealthStatus] = Json.writes[HealthStatus]
}

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

object StorageHealthStatus {
  implicit val storageHealthStatusWrites: OWrites[StorageHealthStatus] = Json.writes[StorageHealthStatus]
}

/**
 * Controller for checking the health of the replay-parser microservice and storage systems
 */
@Singleton
class ReplayParserHealthController @Inject()(
  val controllerComponents: ControllerComponents,
  ws: WSClient,
  configuration: Configuration
)(implicit ec: ExecutionContext) extends BaseController {

  private val logger = Logger(getClass)
  
  // Get the replay parser service URL from configuration, default to localhost for development
  private val replayParserUrl = configuration.getOptional[String]("replayparser.url")
    .getOrElse("http://localhost:5000")
  
  // Storage path for file uploads (mounted via Dokku)
  private val uploadStoragePath = "/app/uploads"

  /**
   * Show the health status page with both parser and storage status
   */
  def healthPage(): Action[AnyContent] = Action.async { implicit request =>
    checkStorageHealth().map { storageHealth =>
      Ok(views.html.replayParserHealth(None, Some(storageHealth)))
    }
  }

  /**
   * Check storage health and return status
   */
  def storageHealth(): Action[AnyContent] = Action.async { implicit request =>
    Future {
      val healthStatus = checkStorageHealthSync()
      if (healthStatus.status == "healthy") {
        Ok(Json.toJson(healthStatus))
      } else {
        ServiceUnavailable(Json.toJson(healthStatus))
      }
    }
  }

  /**
   * Create a test file to verify write access to storage
   */
  def testStorageWrite(): Action[AnyContent] = Action.async { implicit request =>
    Future {
      val testFileName = s"health-test-${System.currentTimeMillis()}.tmp"
      val testFilePath = Paths.get(uploadStoragePath, testFileName)
      
      try {
        // Ensure directory exists
        val dir = Paths.get(uploadStoragePath)
        if (!Files.exists(dir)) {
          Files.createDirectories(dir)
        }
        
        // Write test content
        val testContent = s"Health check test at ${Instant.now()}"
        Files.write(testFilePath, testContent.getBytes())
        
        // Read back and verify
        val readContent = new String(Files.readAllBytes(testFilePath))
        
        // Clean up test file
        Files.deleteIfExists(testFilePath)
        
        if (readContent == testContent) {
          Ok(Json.obj(
            "status" -> "success",
            "message" -> "Storage write/read test successful",
            "path" -> uploadStoragePath,
            "testedAt" -> Instant.now().toString
          ))
        } else {
          InternalServerError(Json.obj(
            "status" -> "error",
            "message" -> "Content verification failed",
            "path" -> uploadStoragePath,
            "testedAt" -> Instant.now().toString
          ))
        }
      } catch {
        case ex: Exception =>
          logger.error(s"Storage write test failed: ${ex.getMessage}", ex)
          ServiceUnavailable(Json.obj(
            "status" -> "error",
            "message" -> s"Storage write test failed: ${ex.getMessage}",
            "path" -> uploadStoragePath,
            "testedAt" -> Instant.now().toString
          ))
      }
    }
  }

  /**
   * List files in storage directory (for debugging)
   */
  def listStorageFiles(): Action[AnyContent] = Action.async { implicit request =>
    Future {
      try {
        val dir = Paths.get(uploadStoragePath)
        if (!Files.exists(dir)) {
          Ok(Json.obj(
            "status" -> "directory_not_found",
            "path" -> uploadStoragePath,
            "files" -> Json.arr()
          ))
        } else {
          val files = Files.list(dir)
          val fileList = files.iterator().asScala.map { path =>
            Json.obj(
              "name" -> path.getFileName.toString,
              "size" -> Files.size(path),
              "lastModified" -> Files.getLastModifiedTime(path).toString,
              "isDirectory" -> Files.isDirectory(path)
            )
          }.toList
          files.close()
          
          Ok(Json.obj(
            "status" -> "success",
            "path" -> uploadStoragePath,
            "fileCount" -> fileList.length,
            "files" -> Json.toJson(fileList)
          ))
        }
      } catch {
        case ex: Exception =>
          logger.error(s"Failed to list storage files: ${ex.getMessage}", ex)
          ServiceUnavailable(Json.obj(
            "status" -> "error",
            "message" -> ex.getMessage,
            "path" -> uploadStoragePath
          ))
      }
    }
  }

  /**
   * Get comprehensive health status including both parser and storage
   */
  def fullHealthCheck(): Action[AnyContent] = Action.async { implicit request =>
    val parserHealthFuture = checkParserHealthAsync()
    val storageHealthFuture = checkStorageHealth()
    
    for {
      parserHealth <- parserHealthFuture
      storageHealth <- storageHealthFuture
    } yield {
      val overallHealthy = parserHealth.status == "healthy" && storageHealth.status == "healthy"
      
      val result = Json.obj(
        "overall" -> (if (overallHealthy) "healthy" else "unhealthy"),
        "parser" -> Json.toJson(parserHealth),
        "storage" -> Json.toJson(storageHealth),
        "checkedAt" -> Instant.now().toString
      )
      
      if (overallHealthy) Ok(result) else ServiceUnavailable(result)
    }
  }

  private def checkStorageHealth(): Future[StorageHealthStatus] = Future {
    checkStorageHealthSync()
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

  private def checkParserHealthAsync(): Future[HealthStatus] = {
    val startTime = System.currentTimeMillis()
    
    ws.url(s"$replayParserUrl/health")
      .withRequestTimeout(scala.concurrent.duration.Duration(10, "seconds"))
      .get()
      .map { response =>
        val responseTime = System.currentTimeMillis() - startTime
        
        if (response.status == 200) {
          HealthStatus(
            service = "replay-parser",
            status = "healthy",
            reachable = true,
            responseTime = Some(responseTime)
          )
        } else {
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
          HealthStatus(
            service = "replay-parser",
            status = "unreachable",
            reachable = false,
            responseTime = Some(responseTime),
            error = Some(ex.getMessage)
          )
      }
  }

  /**
   * Health check endpoint that calls the replay-parser service health endpoint
   */
  def health(): Action[AnyContent] = Action.async { implicit request =>
    val startTime = System.currentTimeMillis()
    
    logger.info(s"Checking health of replay-parser service at $replayParserUrl")
    
    ws.url(s"$replayParserUrl/health")
      .withRequestTimeout(scala.concurrent.duration.Duration(10, "seconds"))
      .get()
      .map { response =>
        val responseTime = System.currentTimeMillis() - startTime
        
        if (response.status == 200) {
          logger.info(s"Replay-parser service is healthy (${responseTime}ms)")
          val healthStatus = HealthStatus(
            service = "replay-parser",
            status = "healthy",
            reachable = true,
            responseTime = Some(responseTime)
          )
          Ok(Json.toJson(healthStatus))
        } else {
          logger.warn(s"Replay-parser service returned status ${response.status}")
          val healthStatus = HealthStatus(
            service = "replay-parser",
            status = "unhealthy",
            reachable = true,
            responseTime = Some(responseTime),
            error = Some(s"HTTP ${response.status}")
          )
          ServiceUnavailable(Json.toJson(healthStatus))
        }
      }
      .recover {
        case ex =>
          val responseTime = System.currentTimeMillis() - startTime
          logger.error(s"Failed to reach replay-parser service: ${ex.getMessage}", ex)
          val healthStatus = HealthStatus(
            service = "replay-parser",
            status = "unreachable",
            reachable = false,
            responseTime = Some(responseTime),
            error = Some(ex.getMessage)
          )
          ServiceUnavailable(Json.toJson(healthStatus))
      }
  }

  /**
   * Extended health check that also shows service information
   */
  def status(): Action[AnyContent] = Action.async { implicit request =>
    val startTime = System.currentTimeMillis()
    
    logger.info(s"Getting detailed status of replay-parser service at $replayParserUrl")
    
    // Call the root endpoint to get service info
    val serviceInfoFuture = ws.url(replayParserUrl)
      .withRequestTimeout(scala.concurrent.duration.Duration(10, "seconds"))
      .get()
    
    // Call the health endpoint
    val healthFuture = ws.url(s"$replayParserUrl/health")
      .withRequestTimeout(scala.concurrent.duration.Duration(10, "seconds"))
      .get()
    
    for {
      serviceResponse <- serviceInfoFuture.recover { case _ => null }
      healthResponse <- healthFuture.recover { case _ => null }
    } yield {
      val responseTime = System.currentTimeMillis() - startTime
      
      val serviceInfo = Option(serviceResponse)
        .filter(_.status == 200)
        .flatMap(r => r.json.asOpt[JsValue])
        .getOrElse(Json.obj())
      
      val isHealthy = Option(healthResponse).exists(_.status == 200)
      
      val result = Json.obj(
        "service" -> "replay-parser",
        "url" -> replayParserUrl,
        "status" -> (if (isHealthy) "healthy" else "unhealthy"),
        "reachable" -> (serviceResponse != null || healthResponse != null),
        "responseTime" -> responseTime,
        "serviceInfo" -> serviceInfo,
        "timestamp" -> java.time.Instant.now().toString
      )
      
      if (isHealthy) {
        Ok(result)
      } else {
        ServiceUnavailable(result)
      }
    }
  }

  /**
   * Test endpoint to check if the parse endpoint is reachable
   */
  def testParse(): Action[AnyContent] = Action.async { implicit request =>
    logger.info("Testing parse endpoint connectivity")
    
    // Just check if the endpoint is reachable with a small test payload
    val testPayload = Json.obj(
      "replayfile" -> "test",
      "filename" -> "test.rep"
    )
    
    ws.url(s"$replayParserUrl/parse-replay")
      .withRequestTimeout(scala.concurrent.duration.Duration(10, "seconds"))
      .post(testPayload)
      .map { response =>
        // We expect this to fail with a 400 (bad base64), but that means the endpoint is reachable
        if (response.status == 400) {
          Ok(Json.obj(
            "status" -> "reachable",
            "message" -> "Parse endpoint is reachable (expected 400 for test data)"
          ))
        } else {
          Ok(Json.obj(
            "status" -> "reachable", 
            "message" -> s"Parse endpoint responded with status ${response.status}"
          ))
        }
      }
      .recover {
        case ex =>
          logger.error(s"Failed to reach parse endpoint: ${ex.getMessage}", ex)
          ServiceUnavailable(Json.obj(
            "status" -> "unreachable",
            "message" -> ex.getMessage
          ))
      }
  }
}
