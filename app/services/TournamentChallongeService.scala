package services

import models.{Tournament, User}
import play.api.libs.ws.WSClient
import play.api.libs.json._
import play.api.Configuration
import play.api.Logger
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.DefaultBodyWritables._
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**
 * Service to integrate with the Challonge API for tournament management.
 */
trait TournamentChallongeService {

  /**
   * Creates a tournament in Challonge with the given participants.
   * If there are fewer than 2 participants, adds fake users to reach at least 2 participants (max 4 fake users).
   *
   * @param tournament   The local tournament
   * @param participants The list of users registered for the tournament
   * @return The Challonge tournament ID
   */
  def createChallongeTournament(tournament: Tournament, participants: List[User]): Future[Long]

  /**
   * Generates fake users to complete the tournament if needed.
   *
   * @param existingParticipants The current participants
   * @return A list of fake users to add (up to 4 fake users max)
   */
  def generateFakeUsers(existingParticipants: List[User]): List[User]

  /**
   * Updates an existing Challonge tournament.
   *
   * @param challongeTournamentId The Challonge tournament ID
   * @param tournament            The updated tournament data
   * @return Success flag
   */
  def updateChallongeTournament(challongeTournamentId: Long, tournament: Tournament): Future[Boolean]

  /**
   * Adds a participant to a Challonge tournament.
   *
   * @param challongeTournamentId The Challonge tournament ID
   * @param participant           The user to add as participant
   * @return Success flag
   */
  def addParticipant(challongeTournamentId: Long, participant: User): Future[Boolean]

  /**
   * Starts a tournament in Challonge.
   *
   * @param challongeTournamentId The Challonge tournament ID
   * @return Success flag
   */
  def startChallongeTournament(challongeTournamentId: Long): Future[Boolean]
}

/**
 * Implementation of TournamentChallongeService that integrates with Challonge API.
 */
@Singleton
class TournamentChallongeServiceImpl @Inject()(
                                                wsClient: WSClient,
                                                configuration: Configuration
                                              )(implicit ec: ExecutionContext) extends TournamentChallongeService {

  private val logger = Logger(this.getClass)

  // Configuration values
  private val challongeApiKey = configuration.get[String]("challonge.api.key")
  private val challongeBaseUrl = configuration.getOptional[String]("challonge.api.baseUrl").getOrElse("https://api.challonge.com/v1")

  // Common headers for Challonge API requests
  private def commonHeaders = Map(
    "Content-Type" -> "application/json",
    "User-Agent" -> "YtChatInteraction-Tournament-System"
  )

  /**
   * Creates a tournament in Challonge with round robin format.
   * Automatically adds fake users if needed to ensure at least 2 participants.
   */
  override def createChallongeTournament(tournament: Tournament, participants: List[User]): Future[Long] = {
    logger.info(s"Creating Challonge tournament for: ${tournament.name} with ${participants.length} participants")

    // Generate fake users if needed
    val fakeUsers = generateFakeUsers(participants)
    val allParticipants = participants ++ fakeUsers

    if (fakeUsers.nonEmpty) {
      logger.info(s"Added ${fakeUsers.length} fake users to tournament: ${fakeUsers.map(_.userName).mkString(", ")}")
    }

    val tournamentData = Json.obj(
      "tournament" -> Json.obj(
        "name" -> tournament.name,
        "description" -> tournament.description.getOrElse(s"Tournament created from ${tournament.name}"),
        "tournament_type" -> "round robin",
        "url" -> generateTournamentUrl(tournament),
        "open_signup" -> false,
        "hold_third_place_match" -> false,
        "pts_for_match_win" -> "1.0",
        "pts_for_match_tie" -> "0.5",
        "pts_for_game_win" -> "0.0",
        "pts_for_game_tie" -> "0.0",
        "pts_for_bye" -> "1.0",
        "swiss_rounds" -> 0,
        "accept_attachments" -> false,
        "hide_forum" -> true,
        "show_rounds" -> true,
        "private" -> false,
        "notify_users_when_matches_open" -> true,
        "notify_users_when_the_tournament_ends" -> true,
        "sequential_pairings" -> false,
        "signup_cap" -> tournament.maxParticipants,
        "start_at" -> tournament.tournamentStartAt.map(_.toString),
        "check_in_duration" -> null
      )
    )

    val request = wsClient
      .url(s"$challongeBaseUrl/tournaments.json")
      .addHttpHeaders(commonHeaders.toSeq: _*)
      .addQueryStringParameters("api_key" -> challongeApiKey)

    for {
      response <- request.post(tournamentData)
      challongeTournamentId <- response.status match {
        case 200 | 201 =>
          logger.info(s"Successfully created Challonge tournament. Response: ${response.body}")
          val json = response.json
          val tournamentId = (json \ "tournament" \ "id").as[Long]
          logger.info(s"Created Challonge tournament with ID: $tournamentId")

          // Add all participants (real + fake) to the tournament
          addAllParticipants(tournamentId, allParticipants).map(_ => tournamentId)

        case status =>
          logger.error(s"Failed to create Challonge tournament. Status: $status, Response: ${response.body}")
          Future.failed(new RuntimeException(s"Failed to create tournament in Challonge: Status $status"))
      }
    } yield challongeTournamentId
  }

  /**
   * Generates fake users to complete the tournament if needed.
   * Will add fake users to reach at least 2 participants, with a maximum of 4 fake users total.
   */
  override def generateFakeUsers(existingParticipants: List[User]): List[User] = {
    val participantCount = existingParticipants.length

    // Define some fake user names
    val fakeUserNames = List(
      "ChallongeBot_Alpha",
      "ChallongeBot_Beta",
      "ChallongeBot_Gamma",
      "ChallongeBot_Delta"
    )

    // Determine how many fake users to add
    val fakeUsersNeeded = if (participantCount == 0) {
      2 // Add 2 fake users if no real participants
    } else if (participantCount == 1) {
      1 // Add 1 fake user if only 1 real participant
    } else {
      0 // No fake users needed if 2 or more real participants
    }

    // Generate fake users with negative IDs to distinguish them from real users
    val fakeUsers = (0 until fakeUsersNeeded).map { index =>
      User(
        userId = -(index + 1), // Negative IDs for fake users
        userName = fakeUserNames(index)
      )
    }.toList

    logger.info(s"Generated ${fakeUsers.length} fake users for tournament with ${participantCount} real participants")
    fakeUsers
  }

  /**
   * Adds all participants to the Challonge tournament.
   */
  private def addAllParticipants(challongeTournamentId: Long, participants: List[User]): Future[List[Boolean]] = {
    logger.info(s"Adding ${participants.length} participants to Challonge tournament $challongeTournamentId")

    val participantFutures = participants.map { user =>
      addParticipant(challongeTournamentId, user)
    }

    Future.sequence(participantFutures)
  }

  /**
   * Adds a participant to a Challonge tournament.
   */
  override def addParticipant(challongeTournamentId: Long, participant: User): Future[Boolean] = {
    logger.debug(s"Adding participant ${participant.userName} to Challonge tournament $challongeTournamentId")

    val participantData = Json.obj(
      "participant" -> Json.obj(
        "name" -> participant.userName,
        "misc" -> s"User ID: ${participant.userId}"
      )
    )

    val request = wsClient
      .url(s"$challongeBaseUrl/tournaments/$challongeTournamentId/participants.json")
      .addHttpHeaders(commonHeaders.toSeq: _*)
      .addQueryStringParameters("api_key" -> challongeApiKey)

    request.post(participantData).map { response =>
      response.status match {
        case 200 | 201 =>
          logger.debug(s"Successfully added participant ${participant.userName} to tournament $challongeTournamentId")
          true
        case status =>
          logger.warn(s"Failed to add participant ${participant.userName}. Status: $status, Response: ${response.body}")
          false
      }
    }.recover {
      case ex =>
        logger.error(s"Error adding participant ${participant.userName} to tournament $challongeTournamentId", ex)
        false
    }
  }

  /**
   * Updates an existing Challonge tournament.
   */
  override def updateChallongeTournament(challongeTournamentId: Long, tournament: Tournament): Future[Boolean] = {
    logger.info(s"Updating Challonge tournament $challongeTournamentId")

    val tournamentData = Json.obj(
      "tournament" -> Json.obj(
        "name" -> tournament.name,
        "description" -> tournament.description.getOrElse(s"Tournament: ${tournament.name}"),
        "start_at" -> tournament.tournamentStartAt.map(_.toString)
      )
    )

    val request = wsClient
      .url(s"$challongeBaseUrl/tournaments/$challongeTournamentId.json")
      .addHttpHeaders(commonHeaders.toSeq: _*)
      .addQueryStringParameters("api_key" -> challongeApiKey)

    request.put(tournamentData).map { response =>
      response.status match {
        case 200 =>
          logger.info(s"Successfully updated Challonge tournament $challongeTournamentId")
          true
        case status =>
          logger.error(s"Failed to update Challonge tournament. Status: $status, Response: ${response.body}")
          false
      }
    }.recover {
      case ex =>
        logger.error(s"Error updating Challonge tournament $challongeTournamentId", ex)
        false
    }
  }

  /**
   * Starts a tournament in Challonge.
   */
  override def startChallongeTournament(challongeTournamentId: Long): Future[Boolean] = {
    logger.info(s"Starting Challonge tournament $challongeTournamentId")

    val request = wsClient
      .url(s"$challongeBaseUrl/tournaments/$challongeTournamentId/start.json")
      .addHttpHeaders(commonHeaders.toSeq: _*)
      .addQueryStringParameters("api_key" -> challongeApiKey)

    request.post("").map { response =>
      response.status match {
        case 200 =>
          logger.info(s"Successfully started Challonge tournament $challongeTournamentId")
          true
        case status =>
          logger.error(s"Failed to start Challonge tournament. Status: $status, Response: ${response.body}")
          false
      }
    }.recover {
      case ex =>
        logger.error(s"Error starting Challonge tournament $challongeTournamentId", ex)
        false
    }
  }

  /**
   * Generates a unique URL identifier for the tournament.
   */
  private def generateTournamentUrl(tournament: Tournament): String = {
    val sanitized = tournament.name
      .toLowerCase
      .replaceAll("[^a-z0-9]", "_")
      .replaceAll("_+", "_")
      .stripPrefix("_")
      .stripSuffix("_")

    s"${sanitized}_${tournament.id}_${System.currentTimeMillis()}"
  }
}
