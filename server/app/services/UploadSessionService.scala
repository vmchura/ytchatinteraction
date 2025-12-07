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
import models.repository.UserRepository
import models.StarCraftModels.ReplayParsed

import java.nio.file.Files

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
      TournamentSession
    ](
      uploadedFileRepository,
      tournamentService,
      userRepository,
      fileStorageService
    ) {}
