package models.viewmodels

import models.ContentCreatorChannel


enum TournamentRegistrationUserStatus:
  case Unregistered, Registered, NotAbleToRegister

case class MatchupReplayCounts(
  vsProtoss: Int,
  vsZerg: Int,
  vsTerran: Int
) {
  def hasEnoughPerMatchup(minPerMatchup: Int = 2): Boolean =
    vsProtoss >= minPerMatchup && vsZerg >= minPerMatchup && vsTerran >= minPerMatchup
  
  def totalReplays: Int = vsProtoss + vsZerg + vsTerran
}

case class TournamentRegistrationRequirements(
  hasEnoughReplays: Boolean,
  hasAvailability: Boolean,
  hasTimezone: Boolean,
  selectedRace: Option[String],
  replayCounts: Option[MatchupReplayCounts] = None,
  requirementsPerRace: Map[String, RaceRegistrationRequirements] = Map.empty
)

case class RaceRegistrationRequirements(
  hasEnoughReplays: Boolean,
  replayCounts: MatchupReplayCounts
)

case class TournamentOpenDataUser(
  id: Long,
  name: String,
  userRegistered: TournamentRegistrationUserStatus,
  contentCreatorInfo: Option[ContentCreatorChannel],
  registrationRequirements: Option[TournamentRegistrationRequirements] = None
)
case class InProgressTournament(id: Long, name: String, challongeID: Long, challongeURL: String)
case class TournamentViewDataForUser(openTournaments: List[TournamentOpenDataUser],
                                     inProgressTournaments: List[InProgressTournament])