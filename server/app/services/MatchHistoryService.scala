package services

import models.MatchStatus.Completed
import models.TournamentMatch
import models.component._
import models.repository.EloRepository
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MatchHistoryService @Inject() (
                                                dbConfigProvider: DatabaseConfigProvider
                                              )(implicit ec: ExecutionContext) extends UserComponent with CasualMatchComponent with TournamentMatchComponent with MatchStatusColumnComponent:
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile

  import dbConfig._
  import profile.api._

  def recentMatches(userID: Long, limit: Int=5): Future[Seq[(String, String, String, String)]] =
    val matchesQ =
      tournamentMatchesTable
        .filter(tm =>
          (tm.firstUserId === userID || tm.secondUserId === userID) &&
            tm.status === Completed
        ).sortBy(_.createdAt.desc)
        .map(tm => (tm.firstUserId, tm.secondUserId, tm.winnerUserId, LiteralColumn("Tournament")))
        .take(limit) ++
        casualMatchesTable
          .filter(tm =>
            (tm.userId === userID || tm.rivalUserId === userID) &&
              tm.status === Completed
          ).sortBy(_.createdAt.desc)
          .map(tm => (tm.userId, tm.rivalUserId, tm.winnerUserId, LiteralColumn("VS")))
          .take(limit)

    val query =
      matchesQ
        .join(usersTable).on(_._1 === _.userId) // first user
        .join(usersTable).on(_._1._2 === _.userId) // second user
        .join(usersTable).on(_._1._1._3 === _.userId) // winner (optional)
        .map {
          case (((matchRow, firstUser), secondUser), winnerUser) =>
            (
              firstUser.userName,
              secondUser.userName,
              winnerUser.userName,
              matchRow._4
            )
        }


    db.run(query.result)
