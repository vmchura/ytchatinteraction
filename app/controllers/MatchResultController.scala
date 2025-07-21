package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.data.*
import play.api.data.Forms.*

import scala.concurrent.{ExecutionContext, Future}
import utils.auth.WithAdmin

case class MatchResultForm(
                            winnerId: Option[Long],
                            resultType: String
                          )

@Singleton
class MatchResultController @Inject()(components: DefaultSilhouetteControllerComponents,
                                      tournamentService: services.TournamentService
                                     )(implicit ec: ExecutionContext) extends SilhouetteController(components) {

  private val matchResultForm = Form(
    mapping(
      "winnerId" -> optional(longNumber),
      "resultType" -> nonEmptyText
    )(MatchResultForm.apply)(r => Some((r.winnerId, r.resultType)))
  )

  def submitResult(tournamentId: Long, matchId: Long): Action[AnyContent] = silhouette.SecuredAction.async { implicit request =>
    matchResultForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(
          Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
            .flashing("error" -> "Invalid form data")
        )
      }, {
        case MatchResultForm(Some(winnerId), "with_winner") =>
          tournamentService.submitMatchResult(tournamentId, matchId, Some(winnerId), "with_winner").map {
            case Right(_) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("success" -> "Match result submitted successfully")
            case Left(error) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("error" -> s"Failed to submit result: $error")
          }

        case MatchResultForm(None, "tie") =>
          tournamentService.submitMatchResult(tournamentId, matchId, None, "tie").map {
            case Right(_) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("success" -> "Match result submitted as tie")
            case Left(error) =>
              Redirect(routes.FileUploadController.uploadFormForMatch(tournamentId, matchId))
                .flashing("error" -> s"Failed to submit result: $error")
          }

        case MatchResultForm(None, "cancelled") =>
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
