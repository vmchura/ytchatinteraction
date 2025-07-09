package services

import models.{Tournament, User, ChallongeMatch, ChallongeParticipant}
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
   * Stores the mapping between users and their Challonge participant IDs.
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
   * Adds a participant to a Challonge tournament and returns the participant ID.
   *
   * @param challongeTournamentId The Challonge tournament ID
   * @param participant           The user to add as participant
   * @return The Challonge participant ID if successful, None otherwise
   */
  def addParticipant(challongeTournamentId: Long, participant: User): Future[Option[Long]]

  /**
   * Starts a tournament in Challonge.
   *
   * @param challongeTournamentId The Challonge tournament ID
   * @return Success flag
   */
  def startChallongeTournament(challongeTournamentId: Long): Future[Boolean]

  /**
   * Gets all matches for a tournament from Challonge.
   *
   * @param challongeTournamentId The Challonge tournament ID
   * @return List of matches from Challonge
   */
  def getMatches(challongeTournamentId: Long): Future[List[ChallongeMatch]]

  /**
   * Gets matches for a specific participant from Challonge.
   *
   * @param challongeTournamentId The Challonge tournament ID
   * @param participantId The Challonge participant ID
   * @return List of matches where the participant is involved
   */
  def getMatchesForParticipant(challongeTournamentId: Long, participantId: Long): Future[List[ChallongeMatch]]
}

/**
 * Implementation of TournamentChallongeService that integrates with Challonge API.
 */
@Singleton
class TournamentChallongeServiceImpl @Inject()(
                                                wsClient: WSClient,
                                                configuration: Configuration,
                                                tournamentChallongeDAO: models.dao.TournamentChallongeDAO
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
          addAllParticipants(tournamentId, allParticipants, tournament.id).map(_ => tournamentId)

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
   * Adds all participants to the Challonge tournament and stores their participant IDs.
   */
  private def addAllParticipants(challongeTournamentId: Long, participants: List[User], tournamentId: Long): Future[List[Option[Long]]] = {
    logger.info(s"Adding ${participants.length} participants to Challonge tournament $challongeTournamentId")

    val participantFutures = participants.map { user =>
      addParticipant(challongeTournamentId, user).flatMap {
        case Some(participantId) =>
          // Only store mapping for real users (not fake users with negative IDs)
          if (user.userId > 0) {
            tournamentChallongeDAO.createChallongeParticipantMapping(
              tournamentId,
              user.userId,
              participantId,
              challongeTournamentId
            ).map(_ => Option(participantId)).recover {
              case ex =>
                logger.error(s"Failed to store Challonge participant mapping for user ${user.userId}", ex)
                Option(participantId) // Still return the participant ID even if mapping storage fails
            }
          } else {
            Future.successful(Option(participantId))
          }
        case None =>
          Future.successful(Option.empty[Long])
      }
    }

    Future.sequence(participantFutures)
  }

  /**
   * Adds a participant to a Challonge tournament and returns the participant ID.
   */
  override def addParticipant(challongeTournamentId: Long, participant: User): Future[Option[Long]] = {
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
          try {
            val json = response.json
            val participantId = (json \ "participant" \ "id").as[Long]
            logger.debug(s"Successfully added participant ${participant.userName} to tournament $challongeTournamentId with ID $participantId")
            Some(participantId)
          } catch {
            case ex: Exception =>
              logger.error(s"Failed to parse participant ID from response for ${participant.userName}. Response: ${response.body}", ex)
              None
          }
        case status =>
          logger.warn(s"Failed to add participant ${participant.userName}. Status: $status, Response: ${response.body}")
          None
      }
    }.recover {
      case ex =>
        logger.error(s"Error adding participant ${participant.userName} to tournament $challongeTournamentId", ex)
        None
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
   * Gets all matches for a tournament from Challonge.
   */
  override def getMatches(challongeTournamentId: Long): Future[List[ChallongeMatch]] = {
    logger.debug(s"Getting matches for Challonge tournament $challongeTournamentId")

    val request = wsClient
      .url(s"$challongeBaseUrl/tournaments/$challongeTournamentId/matches.json")
      .addHttpHeaders(commonHeaders.toSeq: _*)
      .addQueryStringParameters("api_key" -> challongeApiKey)

    request.get().map { response =>
      response.status match {
        case 200 =>
          try {
            val matchesJson = response.json.as[List[JsObject]]
            val matches = matchesJson.map { matchObj =>
              val matchData = (matchObj \ "match").as[JsObject]
              ChallongeMatch(
                id = (matchData \ "id").as[Long],
                state = (matchData \ "state").as[String],
                player1Id = (matchData \ "player1_id").asOpt[Long],
                player2Id = (matchData \ "player2_id").asOpt[Long],
                winnerId = (matchData \ "winner_id").asOpt[Long],
                loserId = (matchData \ "loser_id").asOpt[Long],
                scheduledTime = (matchData \ "scheduled_time").asOpt[String],
                opponent = "Unknown" // Will be set in getMatchesForParticipant
              )
            }
            logger.debug(s"Retrieved ${matches.length} matches for tournament $challongeTournamentId")
            matches
          } catch {
            case ex: Exception =>
              logger.error(s"Failed to parse matches response for tournament $challongeTournamentId. Response: ${response.body}", ex)
              List.empty
          }
        case status =>
          logger.warn(s"Failed to get matches for tournament $challongeTournamentId. Status: $status, Response: ${response.body}")
          List.empty
      }
    }.recover {
      case ex =>
        logger.error(s"Error getting matches for tournament $challongeTournamentId", ex)
        List.empty
    }
  }

  /**
   * Gets matches for a specific participant from Challonge.
   */
  override def getMatchesForParticipant(challongeTournamentId: Long, participantId: Long): Future[List[ChallongeMatch]] = {
    logger.debug(s"Getting matches for participant $participantId in tournament $challongeTournamentId")

    for {
      allMatches <- getMatches(challongeTournamentId)
      participants <- getParticipants(challongeTournamentId)
      participantMap = participants.map(p => p.id -> p.name).toMap
    } yield {
      // Filter matches where the participant is involved
      val userMatches = allMatches.filter { match_ =>
        match_.player1Id.contains(participantId) || match_.player2Id.contains(participantId)
      }

      // Set the opponent name for each match
      userMatches.map { match_ =>
        val opponentId = if (match_.player1Id.contains(participantId)) {
          match_.player2Id
        } else {
          match_.player1Id
        }
        
        val opponentName = opponentId.flatMap(participantMap.get).getOrElse("Unknown")
        
        match_.copy(opponent = opponentName)
      }
    }
  }

  /**
   * Gets all participants for a tournament from Challonge.
   */
  private def getParticipants(challongeTournamentId: Long): Future[List[ChallongeParticipant]] = {
    logger.debug(s"Getting participants for Challonge tournament $challongeTournamentId")

    val request = wsClient
      .url(s"$challongeBaseUrl/tournaments/$challongeTournamentId/participants.json")
      .addHttpHeaders(commonHeaders.toSeq: _*)
      .addQueryStringParameters("api_key" -> challongeApiKey)

    request.get().map { response =>
      response.status match {
        case 200 =>
          try {
            val participantsJson = response.json.as[List[JsObject]]
            val participants = participantsJson.map { participantObj =>
              val participantData = (participantObj \ "participant").as[JsObject]
              ChallongeParticipant(
                id = (participantData \ "id").as[Long],
                name = (participantData \ "name").as[String]
              )
            }
            logger.debug(s"Retrieved ${participants.length} participants for tournament $challongeTournamentId")
            participants
          } catch {
            case ex: Exception =>
              logger.error(s"Failed to parse participants response for tournament $challongeTournamentId. Response: ${response.body}", ex)
              List.empty
          }
        case status =>
          logger.warn(s"Failed to get participants for tournament $challongeTournamentId. Status: $status, Response: ${response.body}")
          List.empty
      }
    }.recover {
      case ex =>
        logger.error(s"Error getting participants for tournament $challongeTournamentId", ex)
        List.empty
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
