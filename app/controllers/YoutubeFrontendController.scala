package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.data.*
import play.api.data.Forms.*
import services.{ActiveLiveStream, YoutubeLiveChatServiceTyped}
import play.api.i18n.{I18nSupport, MessagesApi}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Controller for the YouTube monitoring frontend interface
 */
@Singleton
class YoutubeFrontendController @Inject()(
  val scc: SilhouetteControllerComponents,
  youtubeLiveChatService: YoutubeLiveChatServiceTyped,
  activeLiveStreamService: ActiveLiveStream
)(implicit ec: ExecutionContext) extends SilhouetteController(scc) with I18nSupport with RequestMarkerContext {

  // Form for stream ID input
  val streamIdForm: Form[StreamIdForm] = Form(
    mapping(
      "streamId" -> nonEmptyText
    )(StreamIdForm.apply)(nn => Some(nn.streamId))
  )
  
  // Form for direct live chat ID input
  private val liveChatIdForm: Form[LiveChatIdForm] = Form(
    mapping(
      "liveChatId" -> nonEmptyText,
      "channelID" -> nonEmptyText,
      "title" -> nonEmptyText,
    )(LiveChatIdForm.apply)(nn => Some((nn.liveChatId, nn.channelID, nn.title)))
  )

  /**
   * Show the form to enter a YouTube Stream ID
   */
  def showStreamIdForm: Action[AnyContent] = silhouette.SecuredAction { implicit request =>
    // Create a MessagesRequest from the SecuredRequest
    implicit val messagesRequest: MessagesRequest[AnyContent] = new MessagesRequest[AnyContent](request, messagesApi)
    Ok(views.html.streamId(streamIdForm))
  }
  
  /**
   * Process the stream ID form and retrieve the live chat ID
   */
  def getLiveChatId: Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    // Create a MessagesRequest from the SecuredRequest
    implicit val messagesRequest: MessagesRequest[AnyContent] = new MessagesRequest[AnyContent](request, messagesApi)

    streamIdForm.bindFromRequest().fold(
      formWithErrors => {
        // Form has errors, redisplay the form with error messages
        Future.successful(BadRequest(views.html.streamId(formWithErrors)))
      },
      streamIdData => {
        // Form is valid, retrieve the live chat ID
        youtubeLiveChatService.getLiveChatId(streamIdData.streamId).map {
          case Some(liveChat) =>
            // Live chat ID found, show the details page
            Ok(views.html.liveChatDetails(streamIdData.streamId, liveChat))
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
  def startMonitoring: Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    // Create a MessagesRequest from the SecuredRequest
    implicit val messagesRequest: MessagesRequest[AnyContent] = new MessagesRequest[AnyContent](request, messagesApi)

    liveChatIdForm.bindFromRequest().fold(
      formWithErrors => {
        // Form has errors, redirect to the input form
        Future.successful(Redirect(routes.YoutubeFrontendController.showStreamIdForm())
          .flashing("error" -> "Invalid live chat ID"))
      },
      liveChatData => {
        // Form is valid, start monitoring
        youtubeLiveChatService.startMonitoringLiveChat(liveChatData.liveChatId, liveChatData.channelID, liveChatData.title).map { _ =>
          Ok(views.html.monitoringStatus(liveChatData.liveChatId))
        }
      }
    )
  }
  
  /**
   * Stop monitoring a live chat
   */
  def stopMonitoring(liveChatId: String): Action[AnyContent] = silhouette.SecuredAction { implicit request =>
    // Create a MessagesRequest from the SecuredRequest
    implicit val messagesRequest: MessagesRequest[AnyContent] = new MessagesRequest[AnyContent](request, messagesApi)

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
case class LiveChatIdForm(liveChatId: String, channelID: String, title: String)