package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services.{IpSecurityService, IpStats}
import utils.auth.WithAdmin

import java.time.Instant

@Singleton
class AdminSecurityController @Inject() (
    components: DefaultSilhouetteControllerComponents,
    ipSecurityService: IpSecurityService
) extends SilhouetteController(components) {

  implicit val instantWrites: Writes[Instant] = Writes[Instant](i => JsString(i.toString))
  implicit val ipStatsWrites: OWrites[IpStats] = Json.writes[IpStats]

  def viewStats: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()) { implicit request =>
    val stats = ipSecurityService.getStats
    val sortedStats = stats.toSeq.sortBy(-_._2.suspiciousPathCount)
    Ok(Json.toJson(sortedStats.toMap))
  }

  def viewBlacklist: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()) { implicit request =>
    Ok(Json.toJson(ipSecurityService.getBlacklist))
  }

  def blockIp(ip: String): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()) { implicit request =>
    ipSecurityService.manualBlock(ip)
    Ok(Json.obj("status" -> "blocked", "ip" -> ip))
  }

  def unblockIp(ip: String): Action[AnyContent] = silhouette.SecuredAction(WithAdmin()) { implicit request =>
    ipSecurityService.unblock(ip)
    Ok(Json.obj("status" -> "unblocked", "ip" -> ip))
  }

  def securityDashboard: Action[AnyContent] = silhouette.SecuredAction(WithAdmin()) { implicit request =>
    val stats = ipSecurityService.getStats
    val blacklist = ipSecurityService.getBlacklist
    val sortedStats = stats.toSeq.sortBy(-_._2.suspiciousPathCount).take(50)
    Ok(views.html.securityDashboard(request.identity, sortedStats, blacklist))
  }
}
