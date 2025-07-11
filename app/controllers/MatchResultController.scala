package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.data.*
import play.api.data.Forms.*
import scala.concurrent.{ExecutionContext, Future}
import models.User
import play.silhouette.api.actions.SecuredRequest

case class MatchResultForm(
                            winnerId: Option[Long],
                            resultType: String
                          )

/**
 * Controller for handling match result submissions
 */
@Singleton
class MatchResultController @Inject()(
                                       val scc: SilhouetteControllerComponents,
                                       tournamentService: services.TournamentService
                                     )(implicit ec: ExecutionContext) extends SilhouetteController(scc) {

  private val matchResultForm = Form(
    mapping(
      "winnerId" -> optional(longNumber),
      "resultType" -> nonEmptyText
    )(MatchResultForm.apply)(r => Some((r.winnerId, r.resultType)))
  )

  /**
   * Submit match result
   */
  def submitResult(tournamentId: Long, matchId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    matchResultForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(
          Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
            .flashing("error" -> "Invalid form data")
        )
      }, {
        case MatchResultForm(Some(userId), "with_winner") =>
          println("WIth winner")
          Future.successful(
            Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
          )
        case MatchResultForm(None, "tie") =>
          println("tie")
          Future.successful(
            Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
          )

        case MatchResultForm(None, "cancelled") =>
          println("cancelled")
          Future.successful(
            Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
          )
        case _ => Future.successful(
          Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
            .flashing("error" -> "Invalid form data")
        )
      }
    )
  }

}
