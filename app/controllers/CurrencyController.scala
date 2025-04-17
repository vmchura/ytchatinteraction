package controllers

import forms.{CurrencyTransferForm, Forms}
import models.repository.YtStreamerRepository
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
}