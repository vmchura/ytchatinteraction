package services

import models.User
import play.api.Logging
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import net.logstash.logback.argument.StructuredArguments._
import evolutioncomplete.TUploadStateShared
import upickle.default.Writer
import models.TSessionUploadFile
@Singleton
class UserActivityService @Inject() () extends Logging {

  private val activityLogger = LoggerFactory.getLogger("user.activity")
  private def caseClassToMap(cc: Product): Map[String, String] = {
    val fields = cc.getClass.getDeclaredFields
    fields.map { field =>
      field.setAccessible(true)
      val name = field.getName
      val value = field.get(cc)
      name -> String.valueOf(value)
    }.toMap
  }

  private def trackEvent(
      eventType: String,
      eventData: Map[String, Any] = Map.empty
  )(using user: User): Unit = {
    val args = Seq(
      keyValue("user_id", user.userId),
      keyValue("event_type", eventType)
    ) ++
      eventData.map { case (k, v) => keyValue(s"data_$k", v) }

    activityLogger.info(s"User activity: $eventType", args: _*)
  }

  private def trackUserAction(
      action: String,
      details: Map[String, String] = Map.empty
  )(using User): Unit = {
    trackEvent(action, details)
  }
  def trackFormSubmit(formName: String, data: Product)(using User): Unit =
    trackUserAction(s"form_$formName", caseClassToMap(data))

  def trackLogin(using user: User): Unit = trackUserAction("user_login")

  private def trackUpdateStateShared[SS <: TUploadStateShared[SS]](
      originState: String,
      stateShared: SS
  )(using Writer[SS], User): Unit =
    val jsonState = upickle.default.write(stateShared)
    trackUserAction(
      s"upload_state_$originState",
      Map(
        "uploadState" -> jsonState
      )
    )

  def trackUploadUser[SS <: TUploadStateShared[SS]](
      stateShared: SS
  )(using Writer[SS], User): Unit =
    trackUpdateStateShared("by_user", stateShared)
  def trackResponseServer[SS <: TUploadStateShared[SS]](
      stateShared: SS
  )(using Writer[SS], User): Unit =
    trackUpdateStateShared("server_response", stateShared)
}
