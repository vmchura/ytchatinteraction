package models.repository

import models.*
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

  def getAllElosByUserId(userId: Long): Future[Seq[EloUser]]

  def getAllLogsByUserId(userId: Long): Future[Seq[EloUserLogWithRivalName]]

  def getAllElosWithUserNames(): Future[Seq[EloUserWithName]]
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

  override def getAllElosByUserId(userId: Long): Future[Seq[EloUser]] = {
    db.run(
      eloUsersTable
        .filter(_.userId === userId)
        .result
    )
  }

  override def getAllLogsByUserId(
      userId: Long
  ): Future[Seq[EloUserLogWithRivalName]] = {
    val query = for {
      log <- eloUsersLogTable.filter(_.userId === userId)
      rival <- usersTable.filter(_.userId === log.rivalUserId)
    } yield (log, rival.userName)

    db.run(
      query
        .sortBy(_._1.eventAt.desc)
        .result
    ).map(_.map { case (log, rivalName) =>
      EloUserLogWithRivalName(
        log.id,
        log.userId,
        log.userRace,
        log.rivalUserId,
        rivalName,
        log.rivalRace,
        log.eventAt,
        log.userInitialElo,
        log.rivalInitialElo,
        log.matchId,
        log.casualMatchId,
        log.userNewElo
      )
    })
  }

  override def getAllElosWithUserNames(): Future[Seq[EloUserWithName]] = {
    val query = for {
      elo <- eloUsersTable
      user <- usersTable.filter(_.userId === elo.userId)
    } yield (elo, user.userName)

    db.run(query.result)
      .map(_.map { case (elo, userName) =>
        EloUserWithName(
          elo.userId,
          userName,
          elo.userRace,
          elo.rivalRace,
          elo.elo,
          elo.updatedAt
        )
      })
  }
}
