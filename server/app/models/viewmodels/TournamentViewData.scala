package models.viewmodels

import models.ContentCreatorChannel


enum TournamentRegistrationUserStatus:
  case Unregistered, Registered, NotAbleToRegister

case class TournamentOpenDataUser(id: Long, name: String, userRegistered: TournamentRegistrationUserStatus, contentCreatorInfo: Option[ContentCreatorChannel])
case class InProgressTournament(id: Long, name: String, challongeID: Long, challongeURL: String)
case class TournamentViewDataForUser(openTournaments: List[TournamentOpenDataUser],
                                     inProgressTournaments: List[InProgressTournament])