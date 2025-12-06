package models.component

import models.{EloUser, EloUserLog, StarCraftModels}
import slick.jdbc.JdbcProfile
import java.time.Instant

trait EloComponent extends UserComponent {
  protected val profile: JdbcProfile

  import profile.api._

  given BaseColumnType[StarCraftModels.SCRace] =
    MappedColumnType.base[StarCraftModels.SCRace, String](
      {
        case StarCraftModels.Zerg    => "Zerg"
        case StarCraftModels.Terran  => "Terran"
        case StarCraftModels.Protoss => "Protoss"
      },
      {
        case "Zerg"    => StarCraftModels.Zerg
        case "Terran"  => StarCraftModels.Terran
        case "Protoss" => StarCraftModels.Protoss
      }
    )

  class EloUsersTable(tag: Tag) extends Table[EloUser](tag, "elo_users") {
    def userId = column[Long]("user_id")
    def userRace = column[StarCraftModels.SCRace]("user_race")
    def rivalRace = column[StarCraftModels.SCRace]("rival_race")
    def elo = column[Int]("elo")
    def updatedAt = column[Instant]("updated_at")

    def pk = primaryKey("elo_users_primary_key", (userId, userRace, rivalRace))

    def * = (userId, userRace, rivalRace, elo, updatedAt).mapTo[EloUser]
  }

  class EloUsersLogTable(tag: Tag)
      extends Table[EloUserLog](tag, "elo_users_log") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def userRace = column[StarCraftModels.SCRace]("user_race")
    def rivalUserId = column[Long]("rival_user_id")
    def rivalRace = column[StarCraftModels.SCRace]("rival_race")
    def eventAt = column[Instant]("event_at")
    def userInitialElo = column[Int]("user_initial_elo")
    def rivalInitialElo = column[Int]("rival_initial_elo")
    def matchId = column[Option[Long]]("match_id")
    def casualMatchId = column[Option[Long]]("casual_match_id")
    def userNewElo = column[Int]("user_new_elo")

    def * = (
      id,
      userId,
      userRace,
      rivalUserId,
      rivalRace,
      eventAt,
      userInitialElo,
      rivalInitialElo,
      matchId,
      casualMatchId,
      userNewElo
    ).mapTo[EloUserLog]
  }

  lazy val eloUsersTable = TableQuery[EloUsersTable]
  lazy val eloUsersLogTable = TableQuery[EloUsersLogTable]
}
