package models

import play.api.libs.json._
import java.time.Instant

/**
 * JSON formatters for Tournament models
 */
object TournamentModels {

  // Tournament Status JSON formatters
  implicit val tournamentStatusWrites: Writes[TournamentStatus] = Writes[TournamentStatus] {
    case TournamentStatus.RegistrationOpen => JsString("RegistrationOpen")
    case TournamentStatus.RegistrationClosed => JsString("RegistrationClosed")
    case TournamentStatus.InProgress => JsString("InProgress")
    case TournamentStatus.Completed => JsString("Completed")
    case TournamentStatus.Cancelled => JsString("Cancelled")
  }

  implicit val tournamentStatusReads: Reads[TournamentStatus] = Reads[TournamentStatus] {
    case JsString("RegistrationOpen") => JsSuccess(TournamentStatus.RegistrationOpen)
    case JsString("RegistrationClosed") => JsSuccess(TournamentStatus.RegistrationClosed)
    case JsString("InProgress") => JsSuccess(TournamentStatus.InProgress)
    case JsString("Completed") => JsSuccess(TournamentStatus.Completed)
    case JsString("Cancelled") => JsSuccess(TournamentStatus.Cancelled)
    case other => JsError(s"Unknown tournament status: $other")
  }

  implicit val tournamentStatusFormat: Format[TournamentStatus] = Format(tournamentStatusReads, tournamentStatusWrites)

  // Registration Status JSON formatters
  implicit val registrationStatusWrites: Writes[RegistrationStatus] = Writes[RegistrationStatus] {
    case RegistrationStatus.Registered => JsString("Registered")
    case RegistrationStatus.Confirmed => JsString("Confirmed")
    case RegistrationStatus.Withdrawn => JsString("Withdrawn")
    case RegistrationStatus.DisqualifiedByAdmin => JsString("DisqualifiedByAdmin")
  }

  implicit val registrationStatusReads: Reads[RegistrationStatus] = Reads[RegistrationStatus] {
    case JsString("Registered") => JsSuccess(RegistrationStatus.Registered)
    case JsString("Confirmed") => JsSuccess(RegistrationStatus.Confirmed)
    case JsString("Withdrawn") => JsSuccess(RegistrationStatus.Withdrawn)
    case JsString("DisqualifiedByAdmin") => JsSuccess(RegistrationStatus.DisqualifiedByAdmin)
    case other => JsError(s"Unknown registration status: $other")
  }

  implicit val registrationStatusFormat: Format[RegistrationStatus] = Format(registrationStatusReads, registrationStatusWrites)

  // SCRace JSON formatters
  implicit val scRaceWrites: Writes[StarCraftModels.SCRace] = Writes[StarCraftModels.SCRace] {
    case StarCraftModels.Zerg => JsString("Zerg")
    case StarCraftModels.Terran => JsString("Terran")
    case StarCraftModels.Protoss => JsString("Protoss")
  }

  implicit val scRaceReads: Reads[StarCraftModels.SCRace] = Reads[StarCraftModels.SCRace] {
    case JsString("Zerg") => JsSuccess(StarCraftModels.Zerg)
    case JsString("Terran") => JsSuccess(StarCraftModels.Terran)
    case JsString("Protoss") => JsSuccess(StarCraftModels.Protoss)
    case other => JsError(s"Unknown race: $other")
  }

  implicit val scRaceFormat: Format[StarCraftModels.SCRace] = Format(scRaceReads, scRaceWrites)

  // Instant JSON formatters (if not already available)
  implicit val instantFormat: Format[Instant] = Format[Instant](
    Reads.of[String].map(Instant.parse),
    Writes.of[String].contramap(_.toString)
  )

  // Tournament JSON formatters
  implicit val tournamentFormat: OFormat[Tournament] = Json.format[Tournament]

  // Tournament Registration JSON formatters
  implicit val tournamentRegistrationFormat: OFormat[TournamentRegistration] = Json.format[TournamentRegistration]

  // DTOs for API responses
  case class TournamentSummary(
    id: Long,
    name: String,
    description: Option[String],
    maxParticipants: Int,
    currentParticipants: Int,
    registrationStartAt: Instant,
    registrationEndAt: Instant,
    tournamentStartAt: Option[Instant],
    status: TournamentStatus,
    isRegistrationOpen: Boolean
  )

  implicit val tournamentSummaryFormat: OFormat[TournamentSummary] = Json.format[TournamentSummary]

  case class TournamentRegistrationWithUser(
    registration: TournamentRegistration,
    user: User
  )

  implicit val tournamentRegistrationWithUserFormat: OFormat[TournamentRegistrationWithUser] = Json.format[TournamentRegistrationWithUser]

  case class CreateTournamentRequest(
    name: String,
    description: Option[String],
    maxParticipants: Int,
    registrationStartAt: Instant,
    registrationEndAt: Instant,
    tournamentStartAt: Option[Instant]
  )

  implicit val createTournamentRequestFormat: OFormat[CreateTournamentRequest] = Json.format[CreateTournamentRequest]

  case class UpdateTournamentStatusRequest(
    status: TournamentStatus
  )

  implicit val updateTournamentStatusRequestFormat: OFormat[UpdateTournamentStatusRequest] = Json.format[UpdateTournamentStatusRequest]

  case class SetChallongeTournamentIdRequest(
    challongeTournamentId: Long
  )

  implicit val setChallongeTournamentIdRequestFormat: OFormat[SetChallongeTournamentIdRequest] = Json.format[SetChallongeTournamentIdRequest]
}
