package controllers

import models.repository.UploadedFileRepository
import models.{TournamentMatch, UserSmurf}

import javax.inject.*
import play.api.mvc.*
import play.api.data.*
import play.api.data.Forms.*
import services.{FileProcessResult, UploadSession}
import evolutioncomplete.GameStateShared.ValidGame
import scala.concurrent.{ExecutionContext, Future}
import utils.auth.WithAdmin

case class MatchResultForm(
                            winnerId: Option[Long],
                            resultType: String,
                            inGameSmurfFirst: Option[String],
                            inGameSmurfSecond: Option[String]
                          )

@Singleton
class MatchResultController @Inject()(components: DefaultSilhouetteControllerComponents,
                                      tournamentService: services.TournamentService,
                                      userSmurfService: services.UserSmurfService,
                                      uploadSessionService: services.UploadSessionService,
                                      uploadedFileRepository: UploadedFileRepository
                                     )(implicit ec: ExecutionContext) extends SilhouetteController(components) {

  private val matchResultForm = Form(
    mapping(
      "winnerId" -> optional(longNumber),
      "resultType" -> nonEmptyText,
      "in_game_smurf_first" -> optional(nonEmptyText),
      "in_game_smurf_second" -> optional(nonEmptyText)
    )(MatchResultForm.apply)(r => Some((r.winnerId, r.resultType, r.inGameSmurfFirst, r.inGameSmurfSecond)))
  )
  private def recordMatchSmurfs(matchId: Long, tournamentId: Long, tournamentMatch: TournamentMatch, firstSmurf: String, secondSmurf: String): Future[Seq[UserSmurf]] = {
    userSmurfService.recordMatchSmurfs(
      matchId,
      tournamentId,
      tournamentMatch.firstUserId,
      firstSmurf,
      tournamentMatch.secondUserId,
      secondSmurf
    )
  }
  private def persistMetaDataSessionFiles(session: UploadSession): Future[Int] = {
    Future.sequence(session.uploadState.games.filter{
      case ValidGame(_, _, _, _, _) => true
      case _ => false
    }.flatMap{
      case ValidGame(_, _, _, hash, _) => Some(hash)
      case _ => None
    }.flatMap(hash => session.hash2StoreInformation.get(hash).map((hash, _)).map{ case (hash, storedInfo) =>
          val uploadedFile = models.UploadedFile(
            userId = session.userId,
            tournamentId = session.uploadState.tournamentID,
            matchId = session.matchId,
            sha256Hash = hash,
            originalName = storedInfo.originalFileName,
            relativeDirectoryPath = "uploads", // Based on configuration
            savedFileName = storedInfo.storedFileName,
            uploadedAt = storedInfo.storedAt
          )

          uploadedFileRepository.create(uploadedFile).map { createdFile =>
            logger.info(s"Saved file record to database: ID ${createdFile.id}, SHA256: ${createdFile.sha256Hash}")
            1
          }

    })).map(_.sum)
  }
  

}
