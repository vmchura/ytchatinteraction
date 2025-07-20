package controllers

import forms.Forms
import models.repository.{UserRepository, UserStreamerStateRepository, YtStreamerRepository}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import services.CurrencyTransferService

import utils.auth.WithAdmin
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CurrencyController @Inject()(
  components: DefaultSilhouetteControllerComponents,
  ytStreamerRepository: YtStreamerRepository,
  userRepository: UserRepository,
  userStreamerStateRepository: UserStreamerStateRepository,
  currencyTransferService: CurrencyTransferService,
  override val messagesApi: MessagesApi
)(implicit ec: ExecutionContext) extends SilhouetteController(components) with I18nSupport with RequestMarkerContext {
  
  def showCurrencyForm(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    ytStreamerRepository.getAll().map { streamers =>
      Ok(views.html.currencyTransfer(Forms.currencyTransferForm, streamers))
    }
  }
  
  def addCurrency(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    Forms.currencyTransferForm.bindFromRequest().fold(
      formWithErrors => {
        ytStreamerRepository.getAll().map { streamers =>
          BadRequest(views.html.currencyTransfer(formWithErrors, streamers))
        }
      },
      formData => {
        for {
          streamerOption <- ytStreamerRepository.getByChannelId(formData.channelId)
          result <- streamerOption match {
            case Some(streamer) =>
              ytStreamerRepository.incrementBalance(formData.channelId, formData.amount).map { _ =>
                Redirect(routes.CurrencyController.showCurrencyForm())
                  .flashing("success" -> s"Successfully added ${formData.amount} currency to ${streamer.channelTitle.getOrElse(streamer.channelId)}")
              }
            case None => 
              Future.successful(
                Redirect(routes.CurrencyController.showCurrencyForm())
                  .flashing("error" -> s"Streamer with channel ID ${formData.channelId} not found")
              )
          }
        } yield result
      }
    )
  }
  
  def getStreamerBalance(channelId: String): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    ytStreamerRepository.getBalance(channelId).map {
      case Some(balance) => Ok(s"Current balance: $balance")
      case None => NotFound(s"Streamer with channel ID $channelId not found")
    }
  }

  def showStreamerToUserForm(channelId: String): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    for {
      streamerOption <- ytStreamerRepository.getByChannelId(channelId)
      result <- streamerOption match {
        case Some(streamer) =>
          userStreamerStateRepository.getByStreamerChannelId(channelId).flatMap { userStates =>
            val userIds = userStates.map(_.userId)
            Future.sequence(userIds.map(userId => 
              userRepository.getById(userId).map(user => (userId, user, userStates.find(_.userId == userId).map(_.currentBalanceNumber).getOrElse(0)))
            )).map { userInfoList =>
              val validUserInfoList = userInfoList.collect {
                case (userId, Some(user), balance) => (userId, user, balance)
              }
              Ok(views.html.streamerToUserCurrency(Forms.streamerToUserCurrencyForm, streamer, validUserInfoList))
            }
          }
        case None =>
          Future.successful(
            NotFound(s"Streamer with channel ID $channelId not found")
          )
      }
    } yield result
  }
  
  def transferCurrencyToUser(channelId: String): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    Forms.streamerToUserCurrencyForm.bindFromRequest().fold(
      formWithErrors => {
        for {
          streamerOption <- ytStreamerRepository.getByChannelId(channelId)
          result <- streamerOption match {
            case Some(streamer) =>
              userStreamerStateRepository.getByStreamerChannelId(channelId).flatMap { userStates =>
                val userIds = userStates.map(_.userId)

                Future.sequence(userIds.map(userId => 
                  userRepository.getById(userId).map(user => (userId, user, userStates.find(_.userId == userId).map(_.currentBalanceNumber).getOrElse(0)))
                )).map { userInfoList =>
                  val validUserInfoList = userInfoList.collect {
                    case (userId, Some(user), balance) => (userId, user, balance)
                  }
                  BadRequest(views.html.streamerToUserCurrency(formWithErrors, streamer, validUserInfoList))
                }
              }
            case None =>
              Future.successful(
                NotFound(s"Streamer with channel ID $channelId not found")
              )
          }
        } yield result
      },
      formData => {
        for {
          streamerOption <- ytStreamerRepository.getByChannelId(channelId)
          userOption <- userRepository.getById(formData.userId)
          result <- (streamerOption, userOption) match {
            case (Some(streamer), Some(user)) =>
              currencyTransferService.sendCurrencyFromStreamerToUser(channelId, formData.userId, formData.amount).map {
                case true =>
                  Redirect(routes.CurrencyController.showStreamerToUserForm(channelId))
                    .flashing("success" -> s"Successfully transferred ${formData.amount} currency to user ${user.userName}")
                case false =>
                  Redirect(routes.CurrencyController.showStreamerToUserForm(channelId))
                    .flashing("error" -> "Error occurred during currency transfer")
              }.recover {
                case e: IllegalStateException =>
                  Redirect(routes.CurrencyController.showStreamerToUserForm(channelId))
                    .flashing("error" -> e.getMessage)
              }
            case (None, _) =>
              Future.successful(
                Redirect(routes.HomeController.index())
                  .flashing("error" -> s"Streamer with channel ID $channelId not found")
              )
            case (_, None) =>
              Future.successful(
                Redirect(routes.CurrencyController.showStreamerToUserForm(channelId))
                  .flashing("error" -> s"User with ID ${formData.userId} not found")
              )
          }
        } yield result
      }
    )
  }
  
}