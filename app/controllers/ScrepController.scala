package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import play.api.libs.json._
import play.api.data._
import play.api.data.Forms._
import play.api.{Configuration, Logger}
import services.ScrepService
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.Files.TemporaryFile
import java.io.File

case class ReplayUploadForm(
  includeMap: Boolean = false,
  includeCommands: Boolean = false,
  includeComputed: Boolean = false
)

@Singleton
class ScrepController @Inject()(
  val controllerComponents: ControllerComponents,
  screpService: ScrepService,
  configuration: Configuration
)(implicit ec: ExecutionContext) extends BaseController {

  private val logger = Logger(this.getClass)
  
  private val replayUploadForm = Form(
    mapping(
      "includeMap" -> boolean,
      "includeCommands" -> boolean,
      "includeComputed" -> boolean
    )(ReplayUploadForm.apply)(ReplayUploadForm.unapply)
  )

  /**
   * Show the SCREP upload page
   */
  def index = Action { implicit request =>
    Ok(views.html.screp.index(replayUploadForm))
  }

  /**
   * Health check endpoint
   */
  def health = Action.async { implicit request =>
    screpService.healthCheck().map { isHealthy =>
      if (isHealthy) {
        Ok(Json.obj(
          "status" -> "healthy",
          "screp_service" -> "available",
          "timestamp" -> java.time.Instant.now().toString
        ))
      } else {
        ServiceUnavailable(Json.obj(
          "status" -> "unhealthy",
          "screp_service" -> "unavailable",
          "timestamp" -> java.time.Instant.now().toString
        ))
      }
    }
  }

  /**
   * Get service information
   */
  def serviceInfo = Action.async { implicit request =>
    screpService.getServiceInfo().map { info =>
      Ok(info)
    }
  }

  /**
   * Parse a replay file
   */
  def parseReplay = Action.async(parse.multipartFormData) { implicit request =>
    val formData = replayUploadForm.bindFromRequest()
    
    request.body.file("replay") match {
      case Some(replay) =>
        if (screpService.isValidReplayFile(replay.filename)) {
          val uploadForm = formData.fold(
            _ => ReplayUploadForm(), // Use defaults if form has errors
            identity
          )
          
          screpService.parseReplayFromUpload(
            replay.ref,
            replay.filename,
            uploadForm.includeMap,
            uploadForm.includeCommands,
            uploadForm.includeComputed
          ).map { response =>
            if (response.success) {
              Ok(Json.obj(
                "success" -> true,
                "filename" -> replay.filename,
                "data" -> response.data.getOrElse(JsNull)
              ))
            } else {
              BadRequest(Json.obj(
                "success" -> false,
                "error" -> response.error.getOrElse("Unknown error")
              ))
            }
          }
        } else {
          Future.successful(BadRequest(Json.obj(
            "success" -> false,
            "error" -> "Invalid file type. Only .rep files are supported."
          )))
        }
      case None =>
        Future.successful(BadRequest(Json.obj(
          "success" -> false,
          "error" -> "No replay file provided"
        )))
    }
  }

  /**
   * Get replay overview
   */
  def replayOverview = Action.async(parse.multipartFormData) { implicit request =>
    request.body.file("replay") match {
      case Some(replay) =>
        if (screpService.isValidReplayFile(replay.filename)) {
          screpService.getReplayOverviewFromUpload(
            replay.ref,
            replay.filename
          ).map { response =>
            if (response.success) {
              Ok(Json.obj(
                "success" -> true,
                "filename" -> replay.filename,
                "overview" -> response.data.getOrElse(JsNull)
              ))
            } else {
              BadRequest(Json.obj(
                "success" -> false,
                "error" -> response.error.getOrElse("Unknown error")
              ))
            }
          }
        } else {
          Future.successful(BadRequest(Json.obj(
            "success" -> false,
            "error" -> "Invalid file type. Only .rep files are supported."
          )))
        }
      case None =>
        Future.successful(BadRequest(Json.obj(
          "success" -> false,
          "error" -> "No replay file provided"
        )))
    }
  }

  /**
   * Parse replay and show results in HTML
   */
  def parseAndShow = Action.async(parse.multipartFormData) { implicit request =>
    val formData = replayUploadForm.bindFromRequest()
    
    request.body.file("replay") match {
      case Some(replay) =>
        if (screpService.isValidReplayFile(replay.filename)) {
          val uploadForm = formData.fold(
            _ => ReplayUploadForm(), // Use defaults if form has errors
            identity
          )
          
          screpService.parseReplayFromUpload(
            replay.ref,
            replay.filename,
            uploadForm.includeMap,
            uploadForm.includeCommands,
            uploadForm.includeComputed
          ).map { response =>
            if (response.success) {
              Ok(views.html.screp.results(
                replay.filename,
                response.data,
                isOverview = false
              ))
            } else {
              BadRequest(views.html.screp.index(
                replayUploadForm.fill(uploadForm).withGlobalError(
                  response.error.getOrElse("Unknown error")
                )
              ))
            }
          }
        } else {
          Future.successful(BadRequest(views.html.screp.index(
            replayUploadForm.fill(
              formData.fold(_ => ReplayUploadForm(), identity)
            ).withGlobalError("Invalid file type. Only .rep files are supported.")
          )))
        }
      case None =>
        Future.successful(BadRequest(views.html.screp.index(
          replayUploadForm.bindFromRequest().withGlobalError("No replay file provided")
        )))
    }
  }

  /**
   * Get replay overview and show results in HTML
   */
  def overviewAndShow = Action.async(parse.multipartFormData) { implicit request =>
    request.body.file("replay") match {
      case Some(replay) =>
        if (screpService.isValidReplayFile(replay.filename)) {
          screpService.getReplayOverviewFromUpload(
            replay.ref,
            replay.filename
          ).map { response =>
            if (response.success) {
              Ok(views.html.screp.results(
                replay.filename,
                response.data,
                isOverview = true
              ))
            } else {
              BadRequest(views.html.screp.index(
                replayUploadForm.withGlobalError(
                  response.error.getOrElse("Unknown error")
                )
              ))
            }
          }
        } else {
          Future.successful(BadRequest(views.html.screp.index(
            replayUploadForm.withGlobalError("Invalid file type. Only .rep files are supported.")
          )))
        }
      case None =>
        Future.successful(BadRequest(views.html.screp.index(
          replayUploadForm.withGlobalError("No replay file provided")
        )))
    }
  }
}
