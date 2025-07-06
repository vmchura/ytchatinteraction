package modules

import play.api.inject._
import services.{ParseReplayFileService, DefaultParseReplayFileService}

/**
 * Module for binding services
 */
class ServicesModule extends SimpleModule(
  bind[ParseReplayFileService].to[DefaultParseReplayFileService]
)
