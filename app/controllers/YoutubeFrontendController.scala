package controllers

import javax.inject._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import services.YoutubeLiveChatService

import scala.concurrent.{ExecutionContext, Future}

/**
 * Controller for the YouTube monitoring frontend interface
 */
@Singleton
class YoutubeFrontendController @Inject()(
  cc: MessagesControllerComponents,
  youtubeLiveChatService: YoutubeLiveChatService
)(implicit ec: ExecutionContext) extends MessagesAbstractController(cc) {

  // Form for stream ID input
  val streamIdForm = Form(
    mapping(
      "streamId" -> nonEmptyText
    )(StreamIdForm.apply)(nn => Some(nn.streamId))
  )
  
  // Form for direct live chat ID input
  val liveChatIdForm = Form(
    mapping(
      "liveChatId" -> nonEmptyText
    )(LiveChatIdForm.apply)(nn => Some(nn.liveChatId))
  )

  /**
   * Show the form to enter a YouTube Stream ID
   */
  def showStreamIdForm(): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    Ok(views.html.streamId(streamIdForm))
  }
  
  /**
   * Process the stream ID form and retrieve the live chat ID
   */
  def getLiveChatId(): Action[AnyContent] = Action.async { implicit request: MessagesRequest[AnyContent] =>
    streamIdForm.bindFromRequest().fold(
      formWithErrors => {
        // Form has errors, redisplay the form with error messages
        Future.successful(BadRequest(views.html.streamId(formWithErrors)))
      },
      streamIdData => {
        // Form is valid, retrieve the live chat ID
        youtubeLiveChatService.getLiveChatId(streamIdData.streamId).map {
          case Some(liveChatId) =>
            // Live chat ID found, show the details page
            Ok(views.html.liveChatDetails(streamIdData.streamId, liveChatId))
          case None =>
            // No live chat ID found, redisplay the form with an error
            Redirect(routes.YoutubeFrontendController.showStreamIdForm())
              .flashing("error" -> s"No live chat found for stream ID: ${streamIdData.streamId}")
        }
      }
    )
  }
  
  /**
   * Start monitoring a live chat
   */
  def startMonitoring(): Action[AnyContent] = Action.async { implicit request: MessagesRequest[AnyContent] =>
    liveChatIdForm.bindFromRequest().fold(
      formWithErrors => {
        // Form has errors, redirect to the input form
        Future.successful(Redirect(routes.YoutubeFrontendController.showStreamIdForm())
          .flashing("error" -> "Invalid live chat ID"))
      },
      liveChatData => {
        // Form is valid, start monitoring
        youtubeLiveChatService.startMonitoringLiveChat(liveChatData.liveChatId).map { _ =>
          Ok(views.html.monitoringStatus(liveChatData.liveChatId))
        }
      }
    )
  }
  
  /**
   * Stop monitoring a live chat
   */
  def stopMonitoring(liveChatId: String): Action[AnyContent] = Action { implicit request: MessagesRequest[AnyContent] =>
    youtubeLiveChatService.stopMonitoringLiveChat(liveChatId)
    Redirect(routes.YoutubeFrontendController.showStreamIdForm())
      .flashing("success" -> s"Stopped monitoring live chat: $liveChatId")
  }
}

/**
 * Form data class for Stream ID
 */
case class StreamIdForm(streamId: String)

/**
 * Form data class for Live Chat ID
 */
case class LiveChatIdForm(liveChatId: String)