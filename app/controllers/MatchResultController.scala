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
        case MatchResultForm(Some(winnerId), "with_winner") =>
          // Submit result with winner
          tournamentService.submitMatchResult(tournamentId, matchId, Some(winnerId), "with_winner").map {
            case Right(_) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("success" -> "Match result submitted successfully")
            case Left(error) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("error" -> s"Failed to submit result: $error")
          }

        case MatchResultForm(None, "tie") =>
          // Submit result as tie
          tournamentService.submitMatchResult(tournamentId, matchId, None, "tie").map {
            case Right(_) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("success" -> "Match result submitted as tie")
            case Left(error) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("error" -> s"Failed to submit result: $error")
          }

        case MatchResultForm(None, "cancelled") =>
          // Submit result as cancelled
          tournamentService.submitMatchResult(tournamentId, matchId, None, "cancelled").map {
            case Right(_) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("success" -> "Match cancelled successfully")
            case Left(error) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("error" -> s"Failed to cancel match: $error")
          }

        case _ => Future.successful(
          Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
            .flashing("error" -> "Invalid form data")
        )
      }
    )
  }

}
