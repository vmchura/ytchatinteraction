package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.data.*
import play.api.data.Forms.*
import services.{ActiveLiveStream, YoutubeLiveChatServiceTyped}
import play.api.i18n.I18nSupport
import utils.auth.WithAdmin

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class YoutubeFrontendController @Inject()(
  val scc: SilhouetteControllerComponents,
  youtubeLiveChatService: YoutubeLiveChatServiceTyped,
  activeLiveStreamService: ActiveLiveStream
)(implicit ec: ExecutionContext) extends SilhouetteController(scc) with I18nSupport with RequestMarkerContext {

  val streamIdForm: Form[StreamIdForm] = Form(
    mapping(
      "streamId" -> nonEmptyText
    )(StreamIdForm.apply)(nn => Some(nn.streamId))
  )
  
  private val liveChatIdForm: Form[LiveChatIdForm] = Form(
    mapping(
      "liveChatId" -> nonEmptyText,
      "channelID" -> nonEmptyText,
      "title" -> nonEmptyText,
    )(LiveChatIdForm.apply)(nn => Some((nn.liveChatId, nn.channelID, nn.title)))
  )

  def showStreamIdForm: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()) { implicit request =>
    implicit val messagesRequest: MessagesRequest[AnyContent] = new MessagesRequest[AnyContent](request, messagesApi)
    Ok(views.html.streamId(streamIdForm))
  }
  
  def getLiveChatId: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    implicit val messagesRequest: MessagesRequest[AnyContent] = new MessagesRequest[AnyContent](request, messagesApi)

    streamIdForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.streamId(formWithErrors)))
      },
      streamIdData => {
        youtubeLiveChatService.getLiveChatId(streamIdData.streamId).map {
          case Some(liveChat) =>
            Ok(views.html.liveChatDetails(streamIdData.streamId, liveChat))
          case None =>
            Redirect(routes.YoutubeFrontendController.showStreamIdForm())
              .flashing("error" -> s"No live chat found for stream ID: ${streamIdData.streamId}")
        }
      }
    )
  }
  
  def startMonitoring: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    implicit val messagesRequest: MessagesRequest[AnyContent] = new MessagesRequest[AnyContent](request, messagesApi)

    liveChatIdForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(Redirect(routes.YoutubeFrontendController.showStreamIdForm())
          .flashing("error" -> "Invalid live chat ID"))
      },
      liveChatData => {
        youtubeLiveChatService.startMonitoringLiveChat(liveChatData.liveChatId, liveChatData.channelID, liveChatData.title).map { _ =>
          Ok(views.html.monitoringStatus(liveChatData.liveChatId))
        }
      }
    )
  }
  
  def stopMonitoring(liveChatId: String): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()) { implicit request =>
    implicit val messagesRequest: MessagesRequest[AnyContent] = new MessagesRequest[AnyContent](request, messagesApi)

    youtubeLiveChatService.stopMonitoringLiveChat(liveChatId)
    Redirect(routes.YoutubeFrontendController.showStreamIdForm())
      .flashing("success" -> s"Stopped monitoring live chat: $liveChatId")
  }
}

case class StreamIdForm(streamId: String)

case class LiveChatIdForm(liveChatId: String, channelID: String, title: String)