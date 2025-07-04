package controllers

import javax.inject.*
import play.api.Logger
import play.api.mvc.*
import play.api.libs.ws.WSClient
import play.api.libs.ws.JsonBodyWritables.*
import play.api.libs.json.{JsValue, Json, OWrites}
import play.api.Configuration

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

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

/**
 * Controller for checking the health of the replay-parser microservice
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

  /**
   * Show the health status page
   */
  def healthPage(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.replayParserHealth(None))
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
