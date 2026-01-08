package services

import evolutioncomplete.WinnerShared
import evolutioncomplete.WinnerShared.*
import models.{
  ChallongeMatch,
  ChallongeParticipant,
  MatchStatus,
  Tournament,
  User
}
import play.api.libs.ws.WSClient
import play.api.libs.json.*
import play.api.Configuration
import play.api.Logger
import play.api.libs.ws.JsonBodyWritables.*
import play.api.libs.ws.DefaultBodyWritables.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import io.cequence.openaiscala.domain.settings.TranscriptResponseFormatType.json

/** Service to integrate with the Challonge API for tournament management.
  */
trait TournamentChallongeService {

  /** Creates a tournament in Challonge with the given participants. Stores the
    * mapping between users and their Challonge participant IDs.
    *
    * @param tournament
    *   The local tournament
    * @param participants
    *   The list of users registered for the tournament
    * @return
    *   The Challonge tournament ID
    */
  def createChallongeTournament(
      tournament: Tournament,
      participants: List[User]
  ): Future[(Long, String)]

  /** Updates an existing Challonge tournament.
    *
    * @param challongeTournamentId
    *   The Challonge tournament ID
    * @param tournament
    *   The updated tournament data
    * @return
    *   Success flag
    */
  def updateChallongeTournament(
      challongeTournamentId: Long,
      tournament: Tournament
  ): Future[Boolean]

  /** Adds a participant to a Challonge tournament and returns the participant
    * ID.
    *
    * @param challongeTournamentId
    *   The Challonge tournament ID
    * @param participant
    *   The user to add as participant
    * @return
    *   The Challonge participant ID if successful, None otherwise
    */
  def addParticipant(
      challongeTournamentId: Long,
      participant: User
  ): Future[Option[Long]]

  /** Starts a tournament in Challonge.
    *
    * @param challongeTournamentId
    *   The Challonge tournament ID
    * @return
    *   Success flag
    */
  def startChallongeTournament(challongeTournamentId: Long): Future[Boolean]

  /** Gets all matches for a tournament from Challonge.
    *
    * @param challongeTournamentId
    *   The Challonge tournament ID
    * @return
    *   List of matches from Challonge
    */
  def getMatches(challongeTournamentId: Long): Future[List[ChallongeMatch]]

  /** Gets matches for a specific participant from Challonge.
    *
    * @param challongeTournamentId
    *   The Challonge tournament ID
    * @param participantId
    *   The Challonge participant ID
    * @return
    *   List of matches where the participant is involved
    */
  def getMatchesForParticipant(
      challongeTournamentId: Long,
      participantId: Long
  ): Future[List[ChallongeMatch]]

  /** Gets a specific match by ID from Challonge.
    *
    * @param challongeTournamentId
    *   The Challonge tournament ID
    * @param matchId
    *   The Challonge match ID
    * @return
    *   The match if found, None otherwise
    */
  def getMatch(
      challongeTournamentId: Long,
      matchId: Long
  ): Future[Option[ChallongeMatch]]

  /** Submits match result to Challonge.
    *
    * @param challongeTournamentId
    *   The Challonge tournament ID
    * @param matchId
    *   The Challonge match ID
    * @param player1Id
    *   The Challonge participant ID of player 1
    * @param player2Id
    *   The Challonge participant ID of player 2
    * @return
    *   Success flag
    */
  def submitMatchResult(
      challongeTournamentId: Long,
      matchId: Long,
      player1Id: Long,
      player2Id: Long,
      winner: WinnerShared
  ): Future[Boolean]
}

/** Implementation of TournamentChallongeService that integrates with Challonge
  * API.
  */
@Singleton
class TournamentChallongeServiceImpl @Inject() (
    wsClient: WSClient,
    configuration: Configuration,
    tournamentChallongeDAO: models.dao.TournamentChallongeDAO
)(implicit ec: ExecutionContext)
    extends TournamentChallongeService {

  private val logger = Logger(this.getClass)

  // Configuration values
  private val challongeApiKey = configuration.get[String]("challonge.api.key")
  private val challongeBaseUrl =
    configuration.get[String]("challonge.api.baseUrl")

  // Common headers for Challonge API requests
  private def commonHeaders = Map(
    "Content-Type" -> "application/vnd.api+json",
    "Accept" -> "application/json",
    "Authorization-Type" -> "v1",
    "Authorization" -> challongeApiKey
  )

  override def createChallongeTournament(
      tournament: Tournament,
      participants: List[User]
  ): Future[(Long, String)] = {
    logger.info(
      s"Creating Challonge tournament for: ${tournament.name} with ${participants.length} participants"
    )

    val allParticipants =
      participants ::: ((0 until (10 - participants.length)).map { i =>
        User(
          userId = -(i + 1), // Negative IDs for fake users
          userName = s"Negative_$i"
        )
      }).toList

    val tournamentData = Json.obj(
      "data" -> Json.obj(
        "type" -> "Tournaments",
        "attributes" -> Json.obj(
          "name" -> tournament.name,
          "url" -> generateTournamentUrl(tournament),
          "tournament_type" -> "single elimination",
          "game_name" -> "StarCraft Broodwar",
          "private" -> false,
          "description" -> tournament.description.getOrElse(
            s"Tournament created from ${tournament.name}"
          ),
          "group_stage_enabled" -> true,
          "group_stage_options" -> Json.obj(
            "stage_type" -> "round robin",
            "group_size" -> 5,
            "participant_count_to_advance_per_group" -> 1
          ),
          "hold_third_place_match" -> false,
          "sequential_pairings" -> true
        )
      )
    )

    val request = wsClient
      .url(s"$challongeBaseUrl/tournaments.json")
      .addHttpHeaders(commonHeaders.toSeq: _*)

    for {
      response <- request.post(tournamentData)
      challongeTournamentId <- response.status match {
        case 200 | 201 =>
          logger.info(
            s"Successfully created Challonge tournament. Response: ${response.body}"
          )
          val json_data = response.json \ "data"
          val tournamentId = (json_data \ "id").as[String].toLong
          val challongeFullURL =
            (json_data \ "attributes" \ "url").as[String]
          logger.info(s"Created Challonge tournament with ID: $tournamentId")

          addAllParticipants(tournamentId, allParticipants, tournament.id).map(
            _ => (tournamentId, challongeFullURL)
          )

        case status =>
          logger.error(
            s"Failed to create Challonge tournament. $challongeBaseUrl => Status: $status, Response: |${response.body}|"
          )
          Future.failed(
            new RuntimeException(
              s"Failed to create tournament in Challonge: Status $status"
            )
          )
      }
    } yield challongeTournamentId
  }

  /** Adds all participants to the Challonge tournament and stores their
    * participant IDs.
    */
  private def addAllParticipants(
      challongeTournamentId: Long,
      participants: List[User],
      tournamentId: Long
  ): Future[List[Option[Long]]] = {
    logger.info(
      s"Adding ${participants.length} participants to Challonge tournament $challongeTournamentId"
    )

    val participantFutures = participants.map { user =>
      addParticipant(challongeTournamentId, user).flatMap {
        case Some(participantId) =>
          if (user.userId > 0) {
            tournamentChallongeDAO
              .createChallongeParticipantMapping(
                tournamentId,
                user.userId,
                participantId,
                challongeTournamentId
              )
              .map(_ => Option(participantId))
              .recover { case ex =>
                logger.error(
                  s"Failed to store Challonge participant mapping for user ${user.userId}",
                  ex
                )
                Option(participantId)
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

  /** Adds a participant to a Challonge tournament and returns the participant
    * ID.
    */
  override def addParticipant(
      challongeTournamentId: Long,
      participant: User
  ): Future[Option[Long]] = {
    logger.debug(
      s"Adding participant ${participant.userName} to Challonge tournament $challongeTournamentId"
    )

    val participantData = Json.obj(
      "data" -> Json.obj(
        "type" -> "Participants",
        "attributes" -> Json.obj(
          "name" -> participant.userName,
          "misc" -> participant.userId.toString
        )
      )
    )

    val request = wsClient
      .url(
        s"$challongeBaseUrl/tournaments/$challongeTournamentId/participants.json"
      )
      .addHttpHeaders(commonHeaders.toSeq: _*)

    request
      .post(participantData)
      .map { response =>
        response.status match {
          case 200 | 201 =>
            try {
              val json = response.json
              val participantId = (json \ "data" \ "id").as[String].toLong
              logger.debug(
                s"Successfully added participant ${participant.userName} to tournament $challongeTournamentId with ID $participantId"
              )
              Some(participantId)
            } catch {
              case ex: Exception =>
                logger.error(
                  s"Failed to parse participant ID from response for ${participant.userName}. Response: ${response.body}",
                  ex
                )
                None
            }
          case status =>
            logger.warn(
              s"Failed to add participant ${participant.userName}. Status: $status, Response: ${response.body}"
            )
            None
        }
      }
      .recover { case ex =>
        logger.error(
          s"Error adding participant ${participant.userName} to tournament $challongeTournamentId",
          ex
        )
        None
      }
  }

  /** Updates an existing Challonge tournament.
    */
  override def updateChallongeTournament(
      challongeTournamentId: Long,
      tournament: Tournament
  ): Future[Boolean] = {
    logger.info(s"Updating Challonge tournament $challongeTournamentId")

    val tournamentData = Json.obj(
      "tournament" -> Json.obj(
        "name" -> tournament.name,
        "description" -> tournament.description.getOrElse(
          s"Tournament: ${tournament.name}"
        ),
        "start_at" -> tournament.tournamentStartAt.map(_.toString)
      )
    )

    val request = wsClient
      .url(s"$challongeBaseUrl/tournaments/$challongeTournamentId.json")
      .addHttpHeaders(commonHeaders.toSeq: _*)

    request
      .put(tournamentData)
      .map { response =>
        response.status match {
          case 200 =>
            logger.info(
              s"Successfully updated Challonge tournament $challongeTournamentId"
            )
            true
          case status =>
            logger.error(
              s"Failed to update Challonge tournament. Status: $status, Response: ${response.body}"
            )
            false
        }
      }
      .recover { case ex =>
        logger.error(
          s"Error updating Challonge tournament $challongeTournamentId",
          ex
        )
        false
      }
  }

  /** Starts a tournament in Challonge.
    */
  override def startChallongeTournament(
      challongeTournamentId: Long
  ): Future[Boolean] = {
    logger.info(s"Starting Challonge tournament $challongeTournamentId")

    val request = wsClient
      .url(s"$challongeBaseUrl/tournaments/$challongeTournamentId/start.json")
      .addHttpHeaders(commonHeaders.toSeq: _*)

    request
      .post("")
      .map { response =>
        response.status match {
          case 200 =>
            logger.info(
              s"Successfully started Challonge tournament $challongeTournamentId"
            )
            true
          case status =>
            logger.error(
              s"Failed to start Challonge tournament. Status: $status, Response: ${response.body}"
            )
            false
        }
      }
      .recover { case ex =>
        logger.error(
          s"Error starting Challonge tournament $challongeTournamentId",
          ex
        )
        false
      }
  }

  /** Gets all matches for a tournament from Challonge.
    */
  override def getMatches(
      challongeTournamentId: Long
  ): Future[List[ChallongeMatch]] = {
    logger.debug(
      s"Getting matches for Challonge tournament $challongeTournamentId"
    )

    val request = wsClient
      .url(s"$challongeBaseUrl/tournaments/$challongeTournamentId/matches.json")
      .addHttpHeaders(commonHeaders.toSeq: _*)

    request
      .get()
      .map { response =>
        response.status match {
          case 200 =>
            try {
              val matchesJson = (response.json \ "data").as[List[JsObject]]
              val matches = matchesJson.map { matchObj =>
                val matchData = matchObj \ "attributes"
                ChallongeMatch(
                  id = (matchObj \ "id").as[String].toLong,
                  state = (matchData \ "state").as[String],
                  player1Id =
                    (matchObj \ "relationships" \ "player1" \ "data" \ "id")
                      .asOpt[String]
                      .flatMap(_.toLongOption),
                  player2Id =
                    (matchObj \ "relationships" \ "player2" \ "data" \ "id")
                      .asOpt[String]
                      .flatMap(_.toLongOption),
                  winnerId = (matchData \ "winner_id").asOpt[Long],
                  loserId = (matchData \ "loser_id").asOpt[Long],
                  scheduledTime = (matchData \ "scheduled_time").asOpt[String],
                  opponent = "Unknown",
                  scores_csv = (matchData \ "scores").asOpt[String],
                  winner = None
                )
              }
              logger.debug(
                s"Retrieved ${matches.length} matches for tournament $challongeTournamentId"
              )
              matches
            } catch {
              case ex: Exception =>
                logger.error(
                  s"Failed to parse matches response for tournament $challongeTournamentId. Response: ${response.body}",
                  ex
                )
                List.empty
            }
          case status =>
            logger.warn(
              s"Failed to get matches for tournament $challongeTournamentId. Status: $status, Response: ${response.body}"
            )
            List.empty
        }
      }
      .recover { case ex =>
        logger.error(
          s"Error getting matches for tournament $challongeTournamentId",
          ex
        )
        List.empty
      }
  }

  /** Gets matches for a specific participant from Challonge.
    */
  override def getMatchesForParticipant(
      challongeTournamentId: Long,
      participantId: Long
  ): Future[List[ChallongeMatch]] = {
    logger.debug(
      s"Getting matches for participant $participantId in tournament $challongeTournamentId"
    )

    for {
      allMatches <- getMatches(challongeTournamentId)
      participants <- getParticipants(challongeTournamentId)
      participantMap = participants.map(p => p.id -> p.name).toMap
    } yield {
      // Filter matches where the participant is involved
      val userMatches = allMatches.filter { match_ =>
        match_.player1Id.contains(participantId) || match_.player2Id.contains(
          participantId
        )
      }

      // Set the opponent name for each match
      userMatches.map { match_ =>
        val opponentId = if (match_.player1Id.contains(participantId)) {
          match_.player2Id
        } else {
          match_.player1Id
        }

        val opponentName =
          opponentId.flatMap(participantMap.get).getOrElse("Unknown")

        match_.copy(
          opponent = opponentName,
          winner = match_.winnerId.flatMap(wid =>
            participantMap.get(
              if (wid == participantId) participantId
              else opponentId.getOrElse(0)
            )
          )
        )
      }
    }
  }

  /** Gets all participants for a tournament from Challonge.
    */
  private def getParticipants(
      challongeTournamentId: Long
  ): Future[List[ChallongeParticipant]] = {
    logger.debug(
      s"Getting participants for Challonge tournament $challongeTournamentId"
    )

    val request = wsClient
      .url(
        s"$challongeBaseUrl/tournaments/$challongeTournamentId/participants.json"
      )
      .addHttpHeaders(commonHeaders.toSeq: _*)

    request
      .get()
      .map { response =>
        response.status match {
          case 200 =>
            try {
              val participantsJson = (response.json \ "data").as[List[JsObject]]
              val participants = participantsJson.map { participantObj =>
                ChallongeParticipant(
                  id = (participantObj \ "id").as[String].toLong,
                  name = (participantObj \ "attributes" \ "name").as[String]
                )
              }
              logger.debug(
                s"Retrieved ${participants.length} participants for tournament $challongeTournamentId"
              )
              participants
            } catch {
              case ex: Exception =>
                logger.error(
                  s"Failed to parse participants response for tournament $challongeTournamentId. Response: ${response.body}",
                  ex
                )
                List.empty
            }
          case status =>
            logger.warn(
              s"Failed to get participants for tournament $challongeTournamentId. Status: $status, Response: ${response.body}"
            )
            List.empty
        }
      }
      .recover { case ex =>
        logger.error(
          s"Error getting participants for tournament $challongeTournamentId",
          ex
        )
        List.empty
      }
  }

  /** Gets a specific match by ID from Challonge.
    */
  override def getMatch(
      challongeTournamentId: Long,
      matchId: Long
  ): Future[Option[ChallongeMatch]] = {
    logger.debug(
      s"Getting match $matchId for Challonge tournament $challongeTournamentId"
    )

    val request = wsClient
      .url(
        s"$challongeBaseUrl/tournaments/$challongeTournamentId/matches/$matchId.json"
      )
      .addHttpHeaders(commonHeaders.toSeq: _*)

    request
      .get()
      .map { response =>
        response.status match {
          case 200 =>
            try {
              val matchObj = response.json.as[JsObject] \ "data"
              val matchData = (matchObj \ "attributes").as[JsObject]
              val challongeMatch = ChallongeMatch(
                id = (matchObj \ "id").as[Long],
                state = (matchData \ "state").as[String],
                player1Id =
                  (matchData \ "relationships" \ "player1" \ "data" \ "id")
                    .asOpt[String]
                    .flatMap(_.toLongOption),
                player2Id =
                  (matchData \ "relationships" \ "player2" \ "data" \ "id")
                    .asOpt[String]
                    .flatMap(_.toLongOption),
                winnerId = (matchData \ "winner_id").asOpt[Long],
                loserId = (matchData \ "loser_id").asOpt[Long],
                scheduledTime = (matchData \ "scheduled_time").asOpt[String],
                opponent = "Unknown",
                scores_csv = (matchData \ "scores").asOpt[String],
                winner = None
              )
              logger.debug(
                s"Retrieved match $matchId for tournament $challongeTournamentId"
              )
              Some(challongeMatch)
            } catch {
              case ex: Exception =>
                logger.error(
                  s"Failed to parse match response for match $matchId in tournament $challongeTournamentId. Response: ${response.body}",
                  ex
                )
                None
            }
          case 404 =>
            logger.debug(
              s"Match $matchId not found in tournament $challongeTournamentId"
            )
            None
          case status =>
            logger.warn(
              s"Failed to get match $matchId for tournament $challongeTournamentId. Status: $status, Response: ${response.body}"
            )
            None
        }
      }
      .recover { case ex =>
        logger.error(
          s"Error getting match $matchId for tournament $challongeTournamentId",
          ex
        )
        None
      }
  }

  /** Submits match result to Challonge.
    */
  override def submitMatchResult(
      challongeTournamentId: Long,
      matchId: Long,
      player1Id: Long,
      player2Id: Long,
      winner: WinnerShared
  ): Future[Boolean] = {

    val matchData = winner match {
      case FirstUser =>
        Json.arr(
          Json.obj(
            "participant_id" -> player1Id.toString,
            "score_set" -> "1",
            "advancing" -> true
          ),
          Json.obj(
            "participant_id" -> player2Id.toString,
            "score_set" -> "0",
            "advancing" -> false
          )
        )
      case FirstUserByOnlyPresented =>
        Json.arr(
          Json.obj(
            "participant_id" -> player1Id.toString,
            "score_set" -> "0",
            "advancing" -> true
          ),
          Json.obj(
            "participant_id" -> player2Id.toString,
            "score_set" -> "0",
            "advancing" -> false
          )
        )
      case SecondUser =>
        Json.arr(
          Json.obj(
            "participant_id" -> player1Id.toString,
            "score_set" -> "0",
            "advancing" -> false
          ),
          Json.obj(
            "participant_id" -> player2Id.toString,
            "score_set" -> "1",
            "advancing" -> true
          )
        )
      case SecondUserByOnlyPresented =>
        Json.arr(
          Json.obj(
            "participant_id" -> player1Id.toString,
            "score_set" -> "0",
            "advancing" -> false
          ),
          Json.obj(
            "participant_id" -> player2Id.toString,
            "score_set" -> "0",
            "advancing" -> true
          )
        )
      case Draw | Cancelled | Undefined =>
        Json.arr(
          Json.obj(
            "participant_id" -> player1Id.toString,
            "score_set" -> "0",
            "advancing" -> false
          ),
          Json.obj(
            "participant_id" -> player2Id.toString,
            "score_set" -> "0",
            "advancing" -> false
          )
        )
    }

    val putData = Json.obj(
      "data" -> Json.obj(
        "attributes" -> Json.obj(
          "match" -> matchData,
          "tie" -> {
            winner match {
              case Draw | Cancelled | Undefined => true
              case _                            => false
            }
          }
        )
      )
    )

    val request = wsClient
      .url(
        s"$challongeBaseUrl/tournaments/$challongeTournamentId/matches/$matchId.json"
      )
      .addHttpHeaders(commonHeaders.toSeq: _*)

    request
      .put(putData)
      .map { response =>
        response.status match {
          case 200 =>
            true
          case status =>
            logger.error(
              s"Failed to submit match result for match $matchId. Status: $status, Response: ${response.body}"
            )
            false
        }
      }
      .recover { case ex =>
        logger.error(
          s"Error submitting match result for match $matchId in tournament $challongeTournamentId",
          ex
        )
        false
      }
  }

  /** Generates a unique URL identifier for the tournament.
    */
  private def generateTournamentUrl(tournament: Tournament): String = {
    val sanitized = tournament.name.toLowerCase
      .replaceAll("[^a-z0-9]", "_")
      .replaceAll("_+", "_")
      .stripPrefix("_")
      .stripSuffix("_")

    s"${sanitized}_${tournament.id}_${System.currentTimeMillis()}"
  }
}
