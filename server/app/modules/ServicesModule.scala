package modules

import play.api.inject.*
import services.*
import models.repository.*
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
  bind[YouTubeChannelInfoService].toSelf,
  bind[ContentCreatorChannelService].to[ContentCreatorChannelServiceImpl],
  bind[AnalyticalFileRepository].to[AnalyticalFileRepositoryImpl],
  bind[AnalyticalReplayService].to[AnalyticalReplayServiceImpl],
  bind[AnalyticalResultRepository].to[AnalyticalResultRepositoryImpl],
  bind[EloRepository].to[EloRepositoryImpl]
)