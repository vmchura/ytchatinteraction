package controllers

import models.{AvailabilityStatus, UserAvailability, UserTimezone}
import models.repository.UserAvailabilityRepository
import modules.DefaultEnv
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import play.silhouette.api.Silhouette

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

case class UserTimezoneForm(timezone: String)

case class UserAvailabilityForm(
    fromWeekDay: Int,
    toWeekDay: Int,
    fromHourInclusive: Int,
    toHourExclusive: Int,
    availabilityStatus: String
)

@Singleton
class UserAvailabilityController @Inject()(
    val controllerComponents: ControllerComponents,
    silhouette: Silhouette[DefaultEnv],
    userAvailabilityRepository: UserAvailabilityRepository
)(implicit ec: ExecutionContext)
  extends BaseController with I18nSupport {

  val userTimezoneForm: Form[UserTimezoneForm] = Form(
    mapping(
      "timezone" -> nonEmptyText(minLength = 1, maxLength = 50)
    )(UserTimezoneForm.apply)(u => Some(u.timezone))
  )

  val userAvailabilityForm: Form[UserAvailabilityForm] = Form(
    mapping(
      "fromWeekDay" -> number(min = 1, max = 7),
      "toWeekDay" -> number(min = 1, max = 7),
      "fromHourInclusive" -> number(min = 0, max = 23),
      "toHourExclusive" -> number(min = 1, max = 24),
      "availabilityStatus" -> nonEmptyText.verifying("Invalid availability status", 
        status => List("UNAVAILABLE", "MAYBE_AVAILABLE", "HIGHLY_AVAILABLE").contains(status))
      )(UserAvailabilityForm.apply)(u => (Some((u.fromWeekDay, u.toWeekDay, u.fromHourInclusive, u.toHourExclusive, u.availabilityStatus))))
  )

  def showAvailabilityManagement(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    val userId = request.identity.userId
    
    for {
      timezoneOpt <- userAvailabilityRepository.getTimezone(userId)
      availabilities <- userAvailabilityRepository.getAllAvailabilitiesByUserId(userId)
    } yield {
      Ok(views.html.userAvailabilityManagement(
        request.identity,
        timezoneOpt,
        availabilities.toList,
        userTimezoneForm,
        userAvailabilityForm
      ))
    }
  }

  def updateTimezone(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    val userId = request.identity.userId
    
    userTimezoneForm.bindFromRequest().fold(
      formWithErrors => {
        for {
          timezoneOpt <- userAvailabilityRepository.getTimezone(userId)
          availabilities <- userAvailabilityRepository.getAllAvailabilitiesByUserId(userId)
        } yield {
          BadRequest(views.html.userAvailabilityManagement(
            request.identity,
            timezoneOpt,
            availabilities.toList,
            formWithErrors,
            userAvailabilityForm
          ))
        }
      },
      timezoneData => {
        val userTimezone = UserTimezone(userId, timezoneData.timezone)
        userAvailabilityRepository.updateTimezone(userTimezone).map { _ =>
          Redirect(routes.UserAvailabilityController.showAvailabilityManagement())
            .flashing("success" -> "Timezone updated successfully")
        }
      }
    )
  }

  def addAvailability(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    val userId = request.identity.userId
    
    userAvailabilityForm.bindFromRequest().fold(
      formWithErrors => {
        for {
          timezoneOpt <- userAvailabilityRepository.getTimezone(userId)
          availabilities <- userAvailabilityRepository.getAllAvailabilitiesByUserId(userId)
        } yield {
          BadRequest(views.html.userAvailabilityManagement(
            request.identity,
            timezoneOpt,
            availabilities.toList,
            userTimezoneForm,
            formWithErrors
          ))
        }
      },
      availabilityData => {
        val availabilityStatus = availabilityData.availabilityStatus match {
          case "MAYBE_AVAILABLE" => AvailabilityStatus.MaybeAvailable
          case "HIGHLY_AVAILABLE" => AvailabilityStatus.HighlyAvailable
        }
        
        val userAvailability = UserAvailability(
          userId = userId,
          fromWeekDay = availabilityData.fromWeekDay,
          toWeekDay = availabilityData.toWeekDay,
          fromHourInclusive = availabilityData.fromHourInclusive,
          toHourExclusive = availabilityData.toHourExclusive,
          availabilityStatus = availabilityStatus
        )
        
        userAvailabilityRepository.createAvailability(userAvailability).map { _ =>
          Redirect(routes.UserAvailabilityController.showAvailabilityManagement())
            .flashing("success" -> "Availability added successfully")
        }
      }
    )
  }

  def deleteAvailability(id: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    val userId = request.identity.userId
    
    userAvailabilityRepository.getAvailabilityById(id).flatMap {
      case Some(availability) if availability.userId == request.identity.userId =>
        userAvailabilityRepository.deleteAvailability(id).map { _ =>
          Redirect(routes.UserAvailabilityController.showAvailabilityManagement())
            .flashing("success" -> "Availability deleted successfully")
        }
      case Some(_) =>
        Future.successful(Redirect(routes.UserAvailabilityController.showAvailabilityManagement())
          .flashing("error" -> "You can only delete your own availability"))
      case None =>
        Future.successful(Redirect(routes.UserAvailabilityController.showAvailabilityManagement())
          .flashing("error" -> "Availability not found"))
    }
  }
}
