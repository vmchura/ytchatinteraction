package controllers

import forms.Forms.aliasChangeForm
import forms.AliasChangeForm
import modules.DefaultEnv
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent}
import play.silhouette.api.Silhouette
import services.UserService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AliasController @Inject()(controllerComponents: DefaultSilhouetteControllerComponents,
                                 userService: UserService
                               )(implicit ec: ExecutionContext) extends SilhouetteController(controllerComponents) with I18nSupport {

  def showChangeAliasForm(): Action[AnyContent] = silhouette.SecuredAction { implicit request =>
    val user = request.identity
    Ok(views.html.changeAlias(aliasChangeForm, user))
  }

  def changeAlias(): Action[AnyContent] = SecuredAction.async { implicit request =>
    val user = request.identity

    aliasChangeForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(
          BadRequest(views.html.changeAlias(formWithErrors, user))
        )
      },
      aliasData => {
        val trimmedAlias = aliasData.newAlias.trim

        // First check if the user can use this alias
        userService.canUserUseAlias(user.userId, trimmedAlias).flatMap { canUse =>
          if (!canUse) {
              val formWithError = aliasChangeForm.withError("newAlias", "This alias is already taken by another user.")
              Future.successful(BadRequest(views.html.changeAlias(formWithError, user)))
          } else {
            userService.updateUserAlias(user.userId, trimmedAlias).map { updateResult =>
              if (updateResult > 0) {
                Redirect(routes.AliasController.showChangeAliasForm())
                  .flashing("success" -> s"Your alias has been successfully updated to '$trimmedAlias'.")
              } else {
                Redirect(routes.AliasController.showChangeAliasForm())
                  .flashing("error" -> "Failed to update your alias. Please try again.")
              }
            }.recover {
              case ex: Exception =>
                logger.error("Error updating user alias", ex)
                Redirect(routes.AliasController.showChangeAliasForm())
                  .flashing("error" -> "An error occurred while updating your alias. Please try again.")
            }
          }
        }
      }
    )
  }
}
