package controllers

import models.{PredictionBetType, PredictionMatchup, SavePredictionRequest}
import modules.DefaultEnv
import services.PredictionService
import utils.auth.WithAdmin
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.*
import play.silhouette.api.Silhouette
import org.webjars.play.WebJarsUtil

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PredictionController @Inject()(
  val controllerComponents: ControllerComponents,
  predictionService: PredictionService,
  silhouette: Silhouette[DefaultEnv]
)(implicit ec: ExecutionContext, webJarsUtil: WebJarsUtil) extends BaseController with I18nSupport {

  private val logger = Logger(getClass)

  def showPredictionHome(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    predictionService.getAllEvents.map { events =>
      Ok(views.html.predictions.predictionsHome(request.identity, events))
    }
  }

  def showEventPage(eventId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    implicit val messages: Messages = messagesApi.preferred(request)
    for {
      eventOpt <- predictionService.getEvent(eventId)
      result <- eventOpt match {
        case Some(event) =>
          for {
            matchups <- predictionService.getMatchupsByEvent(eventId)
            bets <- predictionService.getUserBetsForEvent(request.identity.userId, eventId)
            leaderboard <- predictionService.getLeaderboard(eventId)
            pointsByMatchup = bets.flatMap { case (bet, matchup) =>
              predictionService.scorePrediction(bet, matchup).map(matchup.id -> _)
            }.toMap
          } yield Ok(views.html.predictions.predictionsEvent(
            request.identity, event, matchups, bets, leaderboard, pointsByMatchup
          ))
        case None =>
          Future.successful(Redirect(routes.PredictionController.showPredictionHome())
            .flashing("error" -> messages("pred.eventNotFound")))
      }
    } yield result
  }

  def showAdminPredictions(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    predictionService.getAllEvents.map { events =>
      Ok(views.html.predictions.adminPredictions(request.identity, events))
    }
  }

  def showAdminEventPage(eventId: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    implicit val messages: Messages = messagesApi.preferred(request)
    for {
      eventOpt <- predictionService.getEvent(eventId)
      result <- eventOpt match {
        case Some(event) =>
          for {
            matchups <- predictionService.getMatchupsByEvent(eventId)
            leaderboard <- predictionService.getLeaderboard(eventId)
          } yield Ok(views.html.predictions.adminEventDetail(request.identity, event, matchups, leaderboard))
        case None =>
          Future.successful(Redirect(routes.PredictionController.showAdminPredictions())
            .flashing("error" -> messages("pred.eventNotFound")))
      }
    } yield result
  }

  def listEvents(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    predictionService.getAllEvents.map { events =>
      Ok(Json.toJson(events.map(e => eventToJson(e))))
    }
  }

  def getEvent(id: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    predictionService.getEvent(id).map {
      case Some(event) => Ok(eventToJson(event))
      case None => NotFound(Json.obj("error" -> "Event not found"))
    }
  }

  def createEvent(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    request.body.asJson.flatMap(json => (json \ "name").asOpt[String]) match {
      case Some(name) if name.trim.nonEmpty =>
        predictionService.createEvent(name.trim).map { event =>
          Created(eventToJson(event))
        }
      case _ =>
        Future.successful(BadRequest(Json.obj("error" -> "Name is required and cannot be blank")))
    }
  }

  def getMatchups(eventId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    predictionService.getMatchupsByEvent(eventId).map { matchups =>
      Ok(Json.toJson(matchups.map(m => matchupToJson(m))))
    }
  }

  def getMatchupsByRound(eventId: Long, round: Int): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    predictionService.getMatchupsByEventAndRound(eventId, round).map { matchups =>
      Ok(Json.toJson(matchups.map(m => matchupToJson(m))))
    }
  }

  def createMatchup(): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    request.body.asJson.flatMap(json => parseMatchupFromJson(json)) match {
      case Some(matchup) =>
        (for {
          eventOpt <- predictionService.getEvent(matchup.eventId)
          result <- eventOpt match {
            case Some(_) =>
              predictionService.createMatchup(matchup).map { created =>
                Created(matchupToJson(created))
              }
            case None =>
              Future.successful(BadRequest(Json.obj("error" -> "Event not found")))
          }
        } yield result).recoverWith {
          case e: Exception =>
            logger.error("Error creating matchup", e)
            Future.successful(InternalServerError(Json.obj("error" -> "Failed to create matchup")))
        }
      case None =>
        Future.successful(BadRequest(Json.obj("error" -> "Invalid matchup data")))
    }
  }

  def updateMatchup(id: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    request.body.asJson match {
      case Some(json) =>
        parseMatchupFromJson(json) match {
          case Some(matchup) =>
            predictionService.updateMatchup(matchup.copy(id = id)).map {
              case Some(updated) => Ok(matchupToJson(updated))
              case None => NotFound(Json.obj("error" -> "Matchup not found"))
            }
          case None =>
            Future.successful(BadRequest(Json.obj("error" -> "Invalid matchup data")))
        }
      case None =>
        Future.successful(BadRequest(Json.obj("error" -> "Invalid matchup data")))
    }
  }

  def lockMatchup(id: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    predictionService.lockMatchup(id).map {
      case Some(matchup) => Ok(matchupToJson(matchup))
      case None => NotFound(Json.obj("error" -> "Matchup not found"))
    }
  }

  def savePrediction(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    request.body.asJson.flatMap(_.asOpt[SavePredictionRequest]) match {
      case Some(req) =>
        predictionService.savePrediction(request.identity.userId, req.matchupId, req.ranking).map {
          case Right(bet) => Created(Json.obj(
            "id" -> bet.id,
            "matchupId" -> bet.matchupId,
            "ranking" -> bet.ranking
          ))
          case Left(error) => BadRequest(Json.obj("error" -> error))
        }
      case None =>
        Future.successful(BadRequest(Json.obj("error" -> "matchupId and ranking are required")))
    }
  }

  def getUserBets(eventId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    predictionService.getUserBetsForEvent(request.identity.userId, eventId).map { betWithMatchups =>
      Ok(Json.toJson(betWithMatchups.map { case (bet, matchup) =>
        Json.obj(
          "id" -> bet.id,
          "matchupId" -> bet.matchupId,
          "ranking" -> bet.ranking,
          "matchLabel" -> matchup.matchLabel,
          "round" -> matchup.round
        )
      }))
    }
  }

  def deletePrediction(matchupId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    predictionService.deletePrediction(request.identity.userId, matchupId).map {
      case Right(_) => Ok(Json.obj("message" -> "Prediction deleted"))
      case Left(error) => BadRequest(Json.obj("error" -> error))
    }
  }

  def scoreMatchup(id: Long): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()).async { implicit request =>
    request.body.asJson match {
      case Some(json) =>
        predictionService.scoreMatchup(id, json).map {
          case Right(matchup) => Ok(matchupToJson(matchup))
          case Left(error) => BadRequest(Json.obj("error" -> error))
        }
      case None =>
        Future.successful(BadRequest(Json.obj("error" -> "JSON body with result required")))
    }
  }

  def getLeaderboard(eventId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    predictionService.getLeaderboard(eventId).map { scores =>
      Ok(Json.toJson(scores.map(s => Json.obj(
        "userId" -> s.userId,
        "userName" -> s.userName,
        "totalScore" -> s.totalScore
      ))))
    }
  }

  private def eventToJson(e: models.PredictionEvent): JsValue = Json.obj(
    "id" -> e.id,
    "name" -> e.name,
    "status" -> e.status.toString,
    "createdAt" -> e.createdAt.toString
  )

  private def matchupToJson(m: PredictionMatchup): JsValue = Json.obj(
    "id" -> m.id,
    "eventId" -> m.eventId,
    "round" -> m.round,
    "matchLabel" -> m.matchLabel,
    "betType" -> m.betType.toString,
    "player1Name" -> m.player1Name,
    "player1Race" -> m.player1Race,
    "player2Name" -> m.player2Name,
    "player2Race" -> m.player2Race,
    "player3Name" -> m.player3Name,
    "player3Race" -> m.player3Race,
    "player4Name" -> m.player4Name,
    "player4Race" -> m.player4Race,
    "result" -> m.result,
    "status" -> m.status.toString
  )

  private def parseMatchupFromJson(json: JsValue): Option[PredictionMatchup] = {
    for {
      eventId <- (json \ "eventId").asOpt[Long]
      round <- (json \ "round").asOpt[Int]
      matchLabel <- (json \ "matchLabel").asOpt[String]
      betTypeStr <- (json \ "betType").asOpt[String]
      betType <- PredictionBetType.fromStringOpt(betTypeStr)
    } yield PredictionMatchup(
      eventId = eventId,
      round = round,
      matchLabel = matchLabel,
      betType = betType,
      player1Name = (json \ "player1Name").asOpt[String],
      player1Race = (json \ "player1Race").asOpt[String],
      player2Name = (json \ "player2Name").asOpt[String],
      player2Race = (json \ "player2Race").asOpt[String],
      player3Name = (json \ "player3Name").asOpt[String],
      player3Race = (json \ "player3Race").asOpt[String],
      player4Name = (json \ "player4Name").asOpt[String],
      player4Race = (json \ "player4Race").asOpt[String]
    )
  }
}
