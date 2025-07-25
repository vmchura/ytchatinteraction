package controllers

import play.api.i18n.{I18nSupport, Lang, MessagesApi}
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}

import javax.inject.{Inject, Singleton}

@Singleton
class LanguageController @Inject()(
  val controllerComponents: ControllerComponents,
  override val messagesApi: MessagesApi
) extends BaseController with I18nSupport {

  /**
   * Change the user's language preference
   * @param lang The language code to switch to
   * @return Redirect back to the referring page or home
   */
  def changeLanguage(lang: String): Action[AnyContent] = Action { implicit request =>
    val language = Lang(lang)
    val referrer = request.headers.get("Referer").getOrElse(routes.HomeController.index().url)
    
    Redirect(referrer).withLang(language)(messagesApi)
  }
}
