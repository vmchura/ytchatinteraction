package services

import evolutioncomplete.GameStateShared.*
import evolutioncomplete.WinnerShared.Cancelled
import evolutioncomplete._
import models.StarCraftModels.{SCMatchMode, Team}
import models._

import javax.inject.*
import java.util.concurrent.ConcurrentHashMap
import java.time.{Instant, LocalDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import play.api.Logger
import models.TournamentMatch
import models.repository._
import models.StarCraftModels.ReplayParsed
import java.util.UUID

import java.nio.file.Files

@Singleton
class AnalyticalUploadSessionService @Inject() (
    uploadedFileRepository: models.repository.UploadedFileRepository,
    tournamentService: TournamentService,
    userRepository: UserRepository,
    fileStorageService: FileStorageService,
    analyticalFileRepository: models.repository.AnalyticalFileRepository
)(implicit ec: ExecutionContext)
    extends TUploadSessionService[
      AnalyticalFileInfo,
      AnalyticalUploadStateShared,
      AnalyticalSession,
      MetaAnalyticalSession
    ](
      uploadedFileRepository,
      userRepository,
      fileStorageService
    ) {
  private val logger = Logger(getClass)
  override def startSession(
      newSession: MetaAnalyticalSession
  ): Future[Option[AnalyticalSession]] = {
    checkForDuplicateFile(newSession.fileResult).map { isDuplicate =>
      if (isDuplicate) {
        logger.info(
          s"File ${newSession.fileResult.fileName} with SHA256 ${newSession.fileResult.sha256Hash.getOrElse("unknown")} already exists globally, skipping"
        )
        None
      } else {

        newSession.fileResult match {
          case FileProcessResult(_, _, _, _, _, errorMessage, _, None, _) =>
            None
          case FileProcessResult(
                fileName,
                originalSize,
                contentType,
                processedAt,
                success,
                _,
                Some(
                  ReplayParsed(
                    Some(mapName),
                    Some(startTime),
                    _,
                    teams,
                    _,
                    _,
                    _
                  )
                ),
                Some(sha256Hash),
                path
              ) =>
            val analyticalUploadStateShared = AnalyticalUploadStateShared(
              firstParticipant = ParticipantShared(
                userID = newSession.userId,
                userName = "??",
                smurfs = Set.empty
              ),
              games = List(
                ValidGame(
                  smurfs = teams
                    .flatMap(
                      _.participants
                        .map(scplayer => scplayer.name -> scplayer.id)
                    )
                    .toMap,
                  mapName = mapName,
                  playedAt = LocalDateTime.now(),
                  hash = sha256Hash,
                  sessionID = UUID.randomUUID()
                )
              )
            )

            val preSession = AnalyticalSession(
              userId = newSession.userId,
              uploadState = analyticalUploadStateShared,
              storageInfo = None,
              lastUpdated = Instant.now(),
              fileResult = newSession.fileResult
            )
            fileStorageService.storeBasicFile(
              Files.readAllBytes(path),
              fileName,
              newSession.fileResult.contentType,
              newSession.userId,
              preSession
            ) match {
              case Left(error) =>
                None
              case Right(storedInfo) =>
                val updatedSession = preSession.copy(
                  storageInfo = Some(storedInfo)
                )
                Option.when(updatedSession.isValid)(
                  persistState(updatedSession)
                )
            }
          case FileProcessResult(_, _, _, _, _, errorMessage, _, _, _) => None
        }
      }

    }
  }
  private def checkForDuplicateFile(
      fileResult: FileProcessResult
  ): Future[Boolean] = {
    fileResult.sha256Hash match {
      case Some(sha256) =>
        for {
          duplicateByTournaments <- uploadedFileRepository.findBySha256Hash(
            sha256
          )
          duplicateByAnalytical <- analyticalFileRepository.findBySha256Hash(
            sha256
          )
        } yield {
          duplicateByTournaments.isDefined || duplicateByAnalytical.isDefined
        }
      case None =>
        Future.successful(false)
    }

  }
}

@Singleton
class TournamentUploadSessionService @Inject() (
    uploadedFileRepository: models.repository.UploadedFileRepository,
    tournamentService: TournamentService,
    userRepository: UserRepository,
    fileStorageService: FileStorageService
)(implicit ec: ExecutionContext)
    extends TUploadSessionService[
      StoredFileInfo,
      TournamentUploadStateShared,
      TournamentSession,
      MetaTournamentSession
    ](
      uploadedFileRepository,
      userRepository,
      fileStorageService
    ) {

  override def startSession(
      newSession: MetaTournamentSession
  ): Future[Option[TournamentSession]] = {
    val futSession =
      tournamentService
        .getMatch(newSession.tournamentId, newSession.matchId)
        .flatMap {
          case Some(
                TournamentMatch(
                  _,
                  _,
                  firstUserId,
                  secondUserId,
                  winnerUserId,
                  MatchStatus.Pending | MatchStatus.InProgress,
                  _,
                  createdAt
                )
              ) =>
            for {
              firstUserOpt <- userRepository.getById(firstUserId)
              secondUserOpt <- userRepository.getById(secondUserId)
            } yield {
              firstUserOpt.zip(secondUserOpt).map { (firstUser, secondUser) =>
                val session = TournamentSession(
                  userId = newSession.userId,
                  matchId = newSession.matchId,
                  tournamentId = newSession.tournamentId,
                  uploadState = TournamentUploadStateShared(
                    challongeMatchID = newSession.matchId,
                    tournamentID = newSession.tournamentId,
                    firstParticipant = ParticipantShared(
                      firstUser.userId,
                      firstUser.userName,
                      Set.empty[String]
                    ),
                    secondParticipant = ParticipantShared(
                      secondUser.userId,
                      secondUser.userName,
                      Set.empty[String]
                    ),
                    games = Nil,
                    winner = Cancelled
                  ),
                  hash2StoreInformation = Map.empty,
                  lastUpdated = Instant.now()
                )
                persistState(session)
              }

            }
          case _ => Future.successful(None)
        }

    futSession
  }
}

@Singleton
class CasualMatchUploadSessionService @Inject() (
    uploadedFileRepository: models.repository.UploadedFileRepository,
    casualMatchRepository: CasualMatchRepository,
    userRepository: UserRepository,
    fileStorageService: FileStorageService
)(implicit ec: ExecutionContext)
    extends TUploadSessionService[
      CasualMatchFileInfo,
      CasualMatchStateShared,
      CasualMatchSession,
      MetaCasualMatchSession
    ](
      uploadedFileRepository,
      userRepository,
      fileStorageService
    ) {

  override def startSession(
      newSession: MetaCasualMatchSession
  ): Future[Option[CasualMatchSession]] = {
    val futSession =
      casualMatchRepository
        .findById(newSession.casualMatchId)
        .flatMap {
          case Some(
                CasualMatch(
                  casualMatchId,
                  userId,
                  rivalUserId,
                  None,
                  createdAt,
                  MatchStatus.Pending | MatchStatus.InProgress
                )
              ) =>
            for {
              firstUserOpt <- userRepository.getById(userId)
              secondUserOpt <- userRepository.getById(rivalUserId)
            } yield {
              firstUserOpt.zip(secondUserOpt).map { (firstUser, secondUser) =>
                val session = CasualMatchSession(
                  userId = newSession.userId,
                  casualMatchId = casualMatchId,
                  uploadState = CasualMatchStateShared(
                    casualMatchId = casualMatchId,
                    firstParticipant = ParticipantShared(
                      firstUser.userId,
                      firstUser.userName,
                      Set.empty[String]
                    ),
                    secondParticipant = ParticipantShared(
                      secondUser.userId,
                      secondUser.userName,
                      Set.empty[String]
                    ),
                    games = Nil,
                    winner = Cancelled
                  ),
                  hash2StoreInformation = Map.empty,
                  lastUpdated = Instant.now()
                )
                persistState(session)
              }

            }
          case _ => Future.successful(None)
        }

    futSession
  }
}
