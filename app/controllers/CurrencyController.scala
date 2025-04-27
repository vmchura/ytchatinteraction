package controllers

import forms.{CurrencyTransferForm, Forms, StreamerToUserCurrencyForm}
import models.repository.{UserRepository, UserStreamerStateRepository, YtStreamerRepository}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import services.CurrencyTransferService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Controller for managing currency transfers to YouTube streamers
 */
@Singleton
class CurrencyController @Inject()(
  cc: ControllerComponents,
  ytStreamerRepository: YtStreamerRepository,
  userRepository: UserRepository,
  userStreamerStateRepository: UserStreamerStateRepository,
  currencyTransferService: CurrencyTransferService,
  override val messagesApi: MessagesApi
)(implicit ec: ExecutionContext) extends AbstractController(cc) with I18nSupport with RequestMarkerContext {
  
  /**
   * Display the currency transfer form
   */
  def showCurrencyForm(): Action[AnyContent] = Action.async { implicit request =>
    ytStreamerRepository.getAll().map { streamers =>
      Ok(views.html.currencyTransfer(Forms.currencyTransferForm, streamers))
    }
  }
  
  /**
   * Process the currency transfer form submission
   */
  def addCurrency(): Action[AnyContent] = Action.async { implicit request =>
    Forms.currencyTransferForm.bindFromRequest().fold(
      formWithErrors => {
        ytStreamerRepository.getAll().map { streamers =>
          BadRequest(views.html.currencyTransfer(formWithErrors, streamers))
        }
      },
      formData => {
        for {
          // Check if the streamer exists
          streamerOption <- ytStreamerRepository.getByChannelId(formData.channelId)
          result <- streamerOption match {
            case Some(streamer) =>
              // Update the streamer's balance
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
  
  /**
   * Get the current balance of a streamer
   */
  def getStreamerBalance(channelId: String): Action[AnyContent] = Action.async { implicit request =>
    ytStreamerRepository.getBalance(channelId).map {
      case Some(balance) => Ok(s"Current balance: $balance")
      case None => NotFound(s"Streamer with channel ID $channelId not found")
    }
  }
  
  /**
   * Display the streamer to user currency transfer form for the logged-in streamer
   */
  def showStreamerToUserForm(channelId: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      // Get the streamer
      streamerOption <- ytStreamerRepository.getByChannelId(channelId)
      result <- streamerOption match {
        case Some(streamer) =>
          // Get all users subscribed to this streamer's channel
          userStreamerStateRepository.getByStreamerChannelId(channelId).flatMap { userStates =>
            val userIds = userStates.map(_.userId)
            
            // Get user details for each user ID
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
  
  /**
   * Process the streamer to user currency transfer form submission
   */
  def transferCurrencyToUser(channelId: String): Action[AnyContent] = Action.async { implicit request =>
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
          // Check if the streamer exists
          streamerOption <- ytStreamerRepository.getByChannelId(channelId)
          // Check if the user exists
          userOption <- userRepository.getById(formData.userId)
          result <- (streamerOption, userOption) match {
            case (Some(streamer), Some(user)) =>
              // Transfer currency from streamer to user
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
                Redirect(routes.HomeController.home())
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