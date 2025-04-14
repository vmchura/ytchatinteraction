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
  implicit val streamIdRequestFormat: Format[StreamIdRequest] = Json.format[StreamIdRequest]
  implicit val liveChatResponseFormat: Format[LiveChatResponse] = Json.format[LiveChatResponse]

  /**
   * Get live chat ID for a given stream ID
   */
  def getLiveChatId: Action[JsValue] = Action.async(parse.json) { request =>
    request.body.validate[StreamIdRequest] match {
      case JsSuccess(streamRequest, _) =>
        youtubeLiveChatService.getLiveChatId(streamRequest.streamId).map {
          case Some(liveChatId) =>
            Ok(Json.toJson(LiveChatResponse(
              streamId = streamRequest.streamId,
              liveChatId = liveChatId,
              success = true,
              message = "Successfully retrieved live chat ID"
            )))
          case None =>
            NotFound(Json.obj(
              "success" -> false,
              "message" -> s"No live chat found for stream ID: ${streamRequest.streamId}"
            ))
        }.recover {
          case e: Exception =>
            InternalServerError(Json.obj(
              "success" -> false, 
              "message" -> s"Error retrieving live chat ID: ${e.getMessage}"
            ))
        }
      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj(
          "success" -> false,
          "message" -> "Invalid request format",
          "errors" -> JsError.toJson(errors)
        )))
    }
  }
  
  /**
   * Start monitoring a YouTube live chat directly with a live chat ID
   */
  def startMonitoring: Action[JsValue] = Action.async(parse.json) { request =>
    request.body.validate[StartMonitoringRequest] match {
      case JsSuccess(monitoringRequest, _) =>
        youtubeLiveChatService.startMonitoringLiveChat(monitoringRequest.liveChatId)
          .map(_ => Ok(Json.obj(
            "success" -> true,
            "message" -> s"Started monitoring live chat: ${monitoringRequest.liveChatId}"
          )))
          .recover {
            case e: Exception => 
              BadRequest(Json.obj(
                "success" -> false,
                "message" -> e.getMessage
              ))
          }
      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj(
          "success" -> false,
          "message" -> "Invalid request format",
          "errors" -> JsError.toJson(errors)
        )))
    }
  }
  
  /**
   * Start monitoring a YouTube live chat using a stream ID
   * This will first retrieve the live chat ID, then start monitoring
   */
  def startMonitoringByStreamId: Action[JsValue] = Action.async(parse.json) { request =>
    request.body.validate[StreamIdRequest] match {
      case JsSuccess(streamRequest, _) =>
        youtubeLiveChatService.getLiveChatId(streamRequest.streamId).flatMap {
          case Some(liveChatId) =>
            youtubeLiveChatService.startMonitoringLiveChat(liveChatId).map(_ => 
              Ok(Json.obj(
                "success" -> true,
                "streamId" -> streamRequest.streamId,
                "liveChatId" -> liveChatId,
                "message" -> s"Started monitoring live chat for stream: ${streamRequest.streamId}"
              ))
            )
          case None =>
            Future.successful(NotFound(Json.obj(
              "success" -> false,
              "message" -> s"No live chat found for stream ID: ${streamRequest.streamId}"
            )))
        }.recover {
          case e: Exception =>
            InternalServerError(Json.obj(
              "success" -> false, 
              "message" -> s"Error starting monitoring: ${e.getMessage}"
            ))
        }
      case JsError(errors) =>
        Future.successful(BadRequest(Json.obj(
          "success" -> false,
          "message" -> "Invalid request format",
          "errors" -> JsError.toJson(errors)
        )))
    }
  }

  /**
   * Stop monitoring a YouTube live chat
   */
  def stopMonitoring(liveChatId: String): Action[AnyContent] = Action {
    youtubeLiveChatService.stopMonitoringLiveChat(liveChatId)
    Ok(Json.obj(
      "success" -> true,
      "message" -> s"Stopped monitoring live chat: $liveChatId"
    ))
  }
}

/**
 * Case class for start monitoring requests
 */
case class StartMonitoringRequest(liveChatId: String)

/**
 * Case class for stream ID requests
 */
case class StreamIdRequest(streamId: String)

/**
 * Case class for live chat ID response
 */
case class LiveChatResponse(
  streamId: String,
  liveChatId: String,
  success: Boolean,
  message: String
)