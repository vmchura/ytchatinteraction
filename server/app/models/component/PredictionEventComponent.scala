package models.component

import models.{PredictionEventStatus, PredictionEvent}
import play.api.libs.json.Json
import slick.jdbc.JdbcProfile
import slick.ast.BaseTypedType

import java.time.Instant

trait PredictionEventComponent {
  protected val profile: JdbcProfile

  import profile.api.*

  given BaseTypedType[PredictionEventStatus] =
    MappedColumnType.base[PredictionEventStatus, String](
      {
        case PredictionEventStatus.Open      => "Open"
        case PredictionEventStatus.Locked    => "Locked"
        case PredictionEventStatus.Completed => "Completed"
      },
      {
        case "Open"      => PredictionEventStatus.Open
        case "Locked"    => PredictionEventStatus.Locked
        case "Completed" => PredictionEventStatus.Completed
        case s           => throw new IllegalArgumentException(s"Unknown prediction event status: $s")
      }
    )

  given BaseTypedType[Map[Int, String]] =
    MappedColumnType.base[Map[Int, String], String](
      map => Json.toJson(map.map { case (k, v) => k.toString -> v }).toString(),
      str => Json.parse(str).as[Map[String, String]].map { case (k, v) => k.toInt -> v }
    )

  class PredictionEventsTable(tag: Tag)
      extends Table[PredictionEvent](tag, "prediction_events") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def status = column[PredictionEventStatus]("status")
    def roundNames = column[Map[Int, String]]("round_names")
    def createdAt = column[Instant]("created_at")

    def * = (id, name, status, roundNames, createdAt).mapTo[PredictionEvent]
  }

  lazy val predictionEventsTable = TableQuery[PredictionEventsTable]
}
