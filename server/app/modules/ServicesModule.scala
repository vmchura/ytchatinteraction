package modules

import play.api.inject.*
import services.{ContentCreatorChannelService, ContentCreatorChannelServiceImpl, DefaultFileStorageService, DefaultParseReplayFileService, FileStorageService, OAuth2TokenRefreshService, ParseReplayFileService, TournamentChallongeService, TournamentChallongeServiceImpl, TournamentService, TournamentServiceImpl, UploadSessionService, UserSmurfService}
import models.repository.{TournamentChallongeParticipantRepository, TournamentChallongeParticipantRepositoryImpl, UploadedFileRepository, UploadedFileRepositoryImpl, UserAliasRepository, UserSmurfRepository}
import models.dao.{TournamentChallongeDAO, TournamentChallongeDAOImpl}

/**
 * Module for binding services
 */
class ServicesModule extends SimpleModule(
  bind[ParseReplayFileService].to[DefaultParseReplayFileService],
  bind[UploadSessionService].toSelf,
  bind[FileStorageService].to[DefaultFileStorageService],
  bind[TournamentService].to[TournamentServiceImpl],
  bind[TournamentChallongeService].to[TournamentChallongeServiceImpl],
  bind[TournamentChallongeParticipantRepository].to[TournamentChallongeParticipantRepositoryImpl],
  bind[TournamentChallongeDAO].to[TournamentChallongeDAOImpl],
  bind[UserAliasRepository].toSelf.eagerly(),
  bind[UploadedFileRepository].to[UploadedFileRepositoryImpl],
  bind[OAuth2TokenRefreshService].toSelf.eagerly(),
  bind[ContentCreatorChannelService].to[ContentCreatorChannelServiceImpl]
)