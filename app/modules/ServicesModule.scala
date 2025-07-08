package modules

import play.api.inject._
import services.{ParseReplayFileService, DefaultParseReplayFileService, UploadSessionService, TournamentService, TournamentServiceImpl}

/**
 * Module for binding services
 */
class ServicesModule extends SimpleModule(
  bind[ParseReplayFileService].to[DefaultParseReplayFileService],
  bind[UploadSessionService].toSelf.eagerly(),
  bind[TournamentService].to[TournamentServiceImpl]
)
