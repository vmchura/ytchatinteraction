package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services.YoutubeLiveChatService
import scala.concurrent.{ExecutionContext, Future}

/**
 * Controller for managing YouTube live chat monitoring
 */
@Singleton
class YoutubeLiveChatController @Inject()(
  cc: ControllerComponents,
  youtubeLiveChatService: YoutubeLiveChatService
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  // Format for the request
  implicit val startMonitoringRequestFormat: Format[StartMonitoringRequest] = Json.format[StartMonitoringRequest]

  /**
   * Start monitoring a YouTube live chat
   */
  def startMonitoring: Action[JsValue] = Action.async(parse.json) { request =>
    request.body.validate[StartMonitoringRequest] match {
      case JsSuccess(monitoringRequest, _) =>
        youtubeLiveChatService.startMonitoringLiveChat(monitoringRequest.liveChatId)
          .map(_ => Ok(Json.obj(
            "status" -> "success",
            "message" -> s"Started monitoring live chat: ${monitoringRequest.liveChatId}"
          )))
          .recover {
            case e: Exception => 
              BadRequest(Json.obj("error" -> e.getMessage))
          }
      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj("error" -> JsError.toJson(errors))))
    }
  }

  /**
   * Stop monitoring a YouTube live chat
   */
  def stopMonitoring(liveChatId: String): Action[AnyContent] = Action {
    youtubeLiveChatService.stopMonitoringLiveChat(liveChatId)
    Ok(Json.obj(
      "status" -> "success",
      "message" -> s"Stopped monitoring live chat: $liveChatId"
    ))
  }
}

/**
 * Case class for start monitoring requests
 */
case class StartMonitoringRequest(liveChatId: String)