package models.component

import models.{PredictionBetType, PredictionMatchup, PredictionMatchupStatus}
import play.api.libs.json.{JsValue, Json}
import slick.jdbc.JdbcProfile
import slick.ast.BaseTypedType

import java.time.Instant

trait PredictionMatchupComponent {
  protected val profile: JdbcProfile

  import profile.api.*

  given BaseTypedType[PredictionBetType] =
    MappedColumnType.base[PredictionBetType, String](
      {
        case PredictionBetType.GroupPosition => "GroupPosition"
        case PredictionBetType.MatchScore    => "MatchScore"
      },
      {
        case "GroupPosition" => PredictionBetType.GroupPosition
        case "MatchScore"    => PredictionBetType.MatchScore
        case s               => throw new IllegalArgumentException(s"Unknown bet type: $s")
      }
    )

  given BaseTypedType[PredictionMatchupStatus] =
    MappedColumnType.base[PredictionMatchupStatus, String](
      {
        case PredictionMatchupStatus.Open   => "Open"
        case PredictionMatchupStatus.Locked => "Locked"
        case PredictionMatchupStatus.Scored => "Scored"
      },
      {
        case "Open"   => PredictionMatchupStatus.Open
        case "Locked" => PredictionMatchupStatus.Locked
        case "Scored" => PredictionMatchupStatus.Scored
        case s        => throw new IllegalArgumentException(s"Unknown prediction matchup status: $s")
      }
    )

  given BaseTypedType[JsValue] =
    MappedColumnType.base[JsValue, String](
      json => json.toString(),
      str => Json.parse(str)
    )

  class PredictionMatchupsTable(tag: Tag)
      extends Table[PredictionMatchup](tag, "prediction_matchups") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def eventId = column[Long]("event_id")
    def round = column[Int]("round")
    def matchLabel = column[String]("match_label")
    def betType = column[PredictionBetType]("bet_type")
    def player1Name = column[Option[String]]("player1_name")
    def player1Race = column[Option[String]]("player1_race")
    def player2Name = column[Option[String]]("player2_name")
    def player2Race = column[Option[String]]("player2_race")
    def player3Name = column[Option[String]]("player3_name")
    def player3Race = column[Option[String]]("player3_race")
    def player4Name = column[Option[String]]("player4_name")
    def player4Race = column[Option[String]]("player4_race")
    def result = column[Option[JsValue]]("result")
    def status = column[PredictionMatchupStatus]("status")
    def resultScoredAt = column[Option[Instant]]("result_scored_at")

    def * = (
      id,
      eventId,
      round,
      matchLabel,
      betType,
      player1Name,
      player1Race,
      player2Name,
      player2Race,
      player3Name,
      player3Race,
      player4Name,
      player4Race,
      result,
      status,
      resultScoredAt
    ).mapTo[PredictionMatchup]
  }

  lazy val predictionMatchupsTable = TableQuery[PredictionMatchupsTable]
}
