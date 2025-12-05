package models.repository

import models.{EloUser, EloUserLog, StarCraftModels}
import models.StarCraftModels.SCRace
import models.component.EloComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

trait EloRepository {
  def apply_first_user_win(
      firstUserId: Long,
      firstUserRace: SCRace,
      secondUserId: Long,
      secondUserRace: SCRace,
      matchId: Option[Long] = None,
      casualMatchId: Option[Long] = None
  ): Future[Option[Boolean]]
  def getElo(
      userId: Long,
      userRace: SCRace,
      rivalRace: SCRace
  ): Future[Option[EloUser]]
}

@Singleton
class EloRepositoryImpl @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends EloRepository
    with EloComponent {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile

  import dbConfig._
  import profile.api._

  private val DEFAULT_ELO = 1200
  private val K_FACTOR = 40

  private def calculateExpectedScore(ratingA: Int, ratingB: Int): Double = {
    1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0))
  }

  private def calculateNewElo(
      currentElo: Int,
      opponentElo: Int,
      score: Double
  ): Int = {
    val expected = calculateExpectedScore(currentElo, opponentElo)
    (currentElo + K_FACTOR * (score - expected)).round.toInt
  }

  override def apply_first_user_win(
      firstUserId: Long,
      firstUserRace: SCRace,
      secondUserId: Long,
      secondUserRace: SCRace,
      matchId: Option[Long] = None,
      casualMatchId: Option[Long] = None
  ): Future[Option[Boolean]] = {
    val now = Instant.now()

    val action = for {
      firstUserEloOpt <- eloUsersTable
        .filter(u =>
          u.userId === firstUserId && u.userRace === firstUserRace && u.rivalRace === secondUserRace
        )
        .result
        .headOption
      secondUserEloOpt <- eloUsersTable
        .filter(u =>
          u.userId === secondUserId && u.userRace === secondUserRace && u.rivalRace === firstUserRace
        )
        .result
        .headOption

      firstUserCurrentElo = firstUserEloOpt.map(_.elo).getOrElse(DEFAULT_ELO)
      secondUserCurrentElo = secondUserEloOpt.map(_.elo).getOrElse(DEFAULT_ELO)
      firstUserNewElo = calculateNewElo(
        firstUserCurrentElo,
        secondUserCurrentElo,
        1.0
      )
      secondUserNewElo = calculateNewElo(
        secondUserCurrentElo,
        firstUserCurrentElo,
        0.0
      )

      _ <- eloUsersTable.insertOrUpdate(
        EloUser(
          firstUserId,
          firstUserRace,
          secondUserRace,
          firstUserNewElo,
          now
        )
      )
      _ <- eloUsersTable.insertOrUpdate(
        EloUser(
          secondUserId,
          secondUserRace,
          firstUserRace,
          secondUserNewElo,
          now
        )
      )

      firstLog = EloUserLog(
        0L,
        firstUserId,
        firstUserRace,
        secondUserId,
        secondUserRace,
        now,
        firstUserCurrentElo,
        secondUserCurrentElo,
        matchId,
        casualMatchId,
        firstUserNewElo
      )
      secondLog = EloUserLog(
        0L,
        secondUserId,
        secondUserRace,
        firstUserId,
        firstUserRace,
        now,
        secondUserCurrentElo,
        firstUserCurrentElo,
        matchId,
        casualMatchId,
        secondUserNewElo
      )

      _ <- eloUsersLogTable += firstLog
      _ <- eloUsersLogTable += secondLog

    } yield Some(true)

    db.run(action.transactionally).recoverWith { case ex: Exception =>
      Future.successful(None)
    }
  }

  override def getElo(
      userId: Long,
      userRace: SCRace,
      rivalRace: SCRace
  ): Future[Option[EloUser]] = {
    db.run(
      eloUsersTable
        .filter(u =>
          u.userId === userId && u.userRace === userRace && u.rivalRace === rivalRace
        )
        .result
        .headOption
    )
  }
}
