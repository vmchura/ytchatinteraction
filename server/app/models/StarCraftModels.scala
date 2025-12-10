package models

import play.api.libs.json.*
import slick.jdbc.JdbcProfile

import javax.json.JsonArray

/**
 * StarCraft related models for replay parsing
 */
object StarCraftModels {

  /**
   * StarCraft races
   */
  sealed trait SCRace {
    def shortLabel: Char
  }

  case object Zerg extends SCRace {
    val shortLabel = 'Z'
  }

  case object Terran extends SCRace {
    val shortLabel = 'T'
  }

  case object Protoss extends SCRace {
    val shortLabel = 'P'
  }

  implicit val scRaceWrites: Writes[SCRace] = {
    case Zerg => JsString("Zerg")
    case Terran => JsString("Terran")
    case Protoss => JsString("Protoss")
  }

  implicit val scRaceReads: Reads[SCRace] = (json: JsValue) => {
    json.validate[String].flatMap {
      case "Zerg" => JsSuccess(Zerg)
      case "Terran" => JsSuccess(Terran)
      case "Protoss" => JsSuccess(Protoss)
      case other => JsError(s"Unknown race: $other")
    }
  }

  object SCRace {
    def columnType(using profile: JdbcProfile): profile.api.BaseColumnType[SCRace] =
      import profile.api.*

      MappedColumnType.base[StarCraftModels.SCRace, String](
        {
          case StarCraftModels.Zerg => "Zerg"
          case StarCraftModels.Terran => "Terran"
          case StarCraftModels.Protoss => "Protoss"
        },
        {
          case "Zerg" => StarCraftModels.Zerg
          case "Terran" => StarCraftModels.Terran
          case "Protoss" => StarCraftModels.Protoss
        }
      )
  }

  /**
   * StarCraft match modes
   */
  sealed trait SCMatchMode

  case object TopVsBottom extends SCMatchMode

  case object Melee extends SCMatchMode

  case object OneVsOneMode extends SCMatchMode

  case object DangerMode extends SCMatchMode

  case object UnknownMode extends SCMatchMode

  implicit val scMatchModeWrites: Writes[SCMatchMode] = {
    case TopVsBottom => JsString("TopVsBottom")
    case Melee => JsString("Melee")
    case OneVsOneMode => JsString("OneVsOne")
    case DangerMode => JsString("DangerMode")
    case UnknownMode => JsString("UnknownMode")
  }

  implicit val scMatchModeReads: Reads[SCMatchMode] = (json: JsValue) => {
    json.validate[String].flatMap {
      case "TopVsBottom" | "TvB" => JsSuccess(TopVsBottom)
      case "Melee" => JsSuccess(Melee)
      case "OneVsOne" | "1v1" => JsSuccess(OneVsOneMode)
      case "DangerMode" | "FFA" | "UMS" => JsSuccess(DangerMode)
      case _ => JsSuccess(UnknownMode)
    }
  }

  /**
   * StarCraft player
   */
  case class SCPlayer(name: String, race: SCRace, id: Int)

  object SCPlayer {
    implicit val scPlayerFormat: Format[SCPlayer] = Json.format[SCPlayer]
  }

  /**
   * Team in a match
   */
  case class Team(index: Int, participants: List[SCPlayer])

  object Team {
    implicit val teamFormat: Format[Team] = Json.format[Team]
  }

  /**
   * Game information parsed from replay
   */
  sealed trait GameInfo

  case class ReplayParsed(
                           mapName: Option[String],
                           startTime: Option[String],
                           gameMode: SCMatchMode,
                           teams: List[Team],
                           winnerTeamIndex: Int,
                           frames: Option[Int],
                           commands: JsArray
                         ) extends GameInfo

  case object ImpossibleToParse extends GameInfo

  object GameInfo {
    implicit val replayParsedFormat: Format[ReplayParsed] = Json.format[ReplayParsed]

    implicit val gameInfoWrites: Writes[GameInfo] = {
      case rp: ReplayParsed => Json.toJson(rp)
      case ImpossibleToParse => Json.obj("type" -> "ImpossibleToParse")
    }

    implicit val gameInfoReads: Reads[GameInfo] = (json: JsValue) => {
      (json \ "type").asOpt[String] match {
        case Some("ImpossibleToParse") => JsSuccess(ImpossibleToParse)
        case _ => json.validate[ReplayParsed]
      }
    }

    /**
     * Parse GameInfo from Either result
     */
    def parseFromEither(data: Either[String, String]): GameInfo = {
      data match {
        case Left(_) => ImpossibleToParse
        case Right(jsonStr) => parseFromJson(jsonStr)
      }
    }

    /**
     * Parse GameInfo from JSON string response
     */
    def parseFromJson(jsonStr: String): GameInfo = {
      try {
        val json = Json.parse(jsonStr)
        val playersJson = (json \ "Header" \ "Players")
          .getOrElse(JsArray.empty)
          .asInstanceOf[JsArray]
        val mapName = (json \ "Header" \ "Map").asOpt[String]
        val startTime = (json \ "Header" \ "StartTime").asOpt[String]
        val frames = (json \ "Header" \ "Frames").asOpt[Int]
        val commands = (json \ "Commands" \ "Cmds").getOrElse(JsArray.empty).asInstanceOf[JsArray]
        val players = playersJson.value.toList
          .flatMap { p =>
            for {
              _ <- (p \ "Type" \ "Name").asOpt[String].flatMap {
                case "Human" => Some(true)
                case _ => None
              }
              team <- (p \ "Team").asOpt[Int]
              name <- (p \ "Name").asOpt[String]
              id <- (p \ "ID").asOpt[Int]
              race <- {
                (p \ "Race" \ "Name").asOpt[String].flatMap {
                  case "Zerg" => Some(Zerg)
                  case "Terran" => Some(Terran)
                  case "Protoss" => Some(Protoss)
                  case _ => None
                }
              }
            } yield {
              (team, SCPlayer(name, race, id))
            }

          }
          .groupBy(_._1)
          .map { case (team, teamPlayers) => (team, teamPlayers.map(_._2)) }
          .toList
          .map { case (i, players) => Team(i, players) }

        val gameMode: SCMatchMode = {
          (json \ "Header" \ "Type" \ "ShortName").asOpt[String] match {
            case Some("TvB") => TopVsBottom
            case Some("Melee") => Melee
            case Some("1v1") => OneVsOneMode
            case Some("FFA") | Some("UMS") => DangerMode
            case _ => UnknownMode
          }
        }

        (json \ "Computed" \ "WinnerTeam").asOpt[Int] match {
          case Some(winnerTeam) =>
            if (players.exists(t => t.index == winnerTeam)) {
              ReplayParsed(
                mapName,
                startTime,
                gameMode,
                players,
                winnerTeam,
                frames,
                commands
              )
            } else {
              if (players.nonEmpty) {
                ReplayParsed(
                  mapName,
                  startTime,
                  gameMode,
                  players,
                  players.head.index,
                  frames,
                  commands
                )
              } else {
                ImpossibleToParse
              }
            }
          case None =>
            if (players.nonEmpty) {
              ReplayParsed(
                mapName,
                startTime,
                gameMode,
                players,
                players.head.index,
                frames,
                commands
              )
            } else {
              ImpossibleToParse
            }
        }
      } catch {
        case _: Exception => ImpossibleToParse
      }
    }
  }
}
