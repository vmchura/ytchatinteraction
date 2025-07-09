package modules

import play.api.inject._
import services.{ParseReplayFileService, DefaultParseReplayFileService, UploadSessionService, TournamentService, TournamentServiceImpl, TournamentChallongeService, TournamentChallongeServiceImpl}
import models.repository.{TournamentChallongeParticipantRepository, TournamentChallongeParticipantRepositoryImpl}
import models.dao.{TournamentChallongeDAO, TournamentChallongeDAOImpl}

/**
 * Module for binding services
 */
class ServicesModule extends SimpleModule(
  bind[ParseReplayFileService].to[DefaultParseReplayFileService],
  bind[UploadSessionService].toSelf.eagerly(),
  bind[TournamentService].to[TournamentServiceImpl],
  bind[TournamentChallongeService].to[TournamentChallongeServiceImpl],
  bind[TournamentChallongeParticipantRepository].to[TournamentChallongeParticipantRepositoryImpl],
  bind[TournamentChallongeDAO].to[TournamentChallongeDAOImpl]
)
