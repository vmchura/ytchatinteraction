package models.repository

import models.{User, UserAliasHistory}
import models.component.{UserAliasComponent, UserComponent}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.dbio.DBIO

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class UserAliasRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)
                                   (implicit ec: ExecutionContext) extends UserAliasComponent with UserComponent {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile

  import dbConfig._
  import profile.api._

  def getUserAliasHistory(userId: Long): Future[Seq[UserAliasHistory]] = db.run {
    userAliasHistoryTable
      .filter(_.userId === userId)
      .sortBy(_.assignedAt.desc)
      .result
  }

  def getCurrentAlias(userId: Long): Future[Option[String]] = db.run {
    userAliasHistoryTable
      .filter(h => h.userId === userId && h.isCurrent)
      .map(_.alias)
      .result
      .headOption
  }

  def isAliasAvailable(alias: String): Future[Boolean] = db.run {
    userAliasHistoryTable
      .filter(h => h.alias === alias)
      .exists
      .result
      .map(!_)
  }

  /**
   * Checks if an alias can be used by a specific user.
   * An alias can be used if:
   * 1. It's completely new (never used before), OR
   * 2. It was previously used by the same user
   *
   * @param userId The user ID requesting the alias
   * @param alias  The alias to check
   * @return Future[Boolean] true if the alias can be used by this user
   */
  def canUserUseAlias(userId: Long, alias: String): Future[Boolean] = db.run {
    canUserUseAliasAction(userId, alias)
  }

  private def canUserUseAliasAction(userId: Long, alias: String): DBIO[Boolean] = {
    userAliasHistoryTable
      .filter(_.alias === alias)
      .result
      .map { existingRecords =>
        existingRecords.isEmpty || existingRecords.forall(_.userId == userId)
      }
  }

  /**
   * Marks the current alias as replaced and sets the replaced_at timestamp.
   *
   * @param userId The user ID
   * @return DBIO action that updates the current alias
   */
  def markCurrentAliasAsReplacedAction(userId: Long): DBIO[Int] = {
    userAliasHistoryTable
      .filter(h => h.userId === userId && h.isCurrent)
      .map(h => (h.isCurrent, h.replacedAt))
      .update((false, Some(Instant.now())))
  }

  /**
   * Reactivates a previously used alias for the same user.
   * This updates the existing record instead of creating a new one.
   *
   * @param userId The user ID
   * @param alias  The alias to reactivate
   * @return DBIO action that reactivates the alias
   */
  def reactivateAliasAction(userId: Long, alias: String): DBIO[Int] = {
    userAliasHistoryTable
      .filter(h => h.userId === userId && h.alias === alias)
      .map(h => (h.isCurrent, h.assignedAt, h.replacedAt))
      .update((true, Instant.now(), None))
  }

  /**
   * Changes a user's alias with full transaction support.
   * This method handles both new aliases and reactivating previously used aliases.
   *
   * @param userId   The user ID
   * @param newAlias The new alias
   * @return Future[Boolean] true if the operation was successful
   */
  def changeUserAlias(userId: Long, newAlias: String): Future[Boolean] = {
    val action = for {
      canUse <- canUserUseAliasAction(userId, newAlias)

      result <- if (canUse) {
        for {
          _ <- markCurrentAliasAsReplacedAction(userId)

          existingForUser <- userAliasHistoryTable
            .filter(h => h.userId === userId && h.alias === newAlias)
            .result
            .headOption

          _ <- existingForUser match {
            case Some(_) =>
              reactivateAliasAction(userId, newAlias)
            case None =>
              addAlias(newAlias, userId, method="user_change").map(_ => 1)
          }
          _ <- updateUserAction(User(userId, newAlias))
        } yield true
      } else {
        DBIO.successful(false)
      }
    } yield result

    db.run(action.transactionally)
  }

}
