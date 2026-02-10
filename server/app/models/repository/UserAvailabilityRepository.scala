package models.repository

import models.{UserAvailability, UserTimezone}
import models.component.UserAvailabilityComponent
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class UserAvailabilityRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) 
    extends UserAvailabilityComponent {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*

  // UserTimezone CRUD methods
  
  def createTimezoneAction(userTimezone: UserTimezone): DBIO[Int] = {
    userTimezonesTable += userTimezone
  }

  def createTimezone(userTimezone: UserTimezone): Future[Int] = {
    db.run(createTimezoneAction(userTimezone))
  }

  def getTimezone(userId: Long): Future[Option[UserTimezone]] = db.run {
    userTimezonesTable.filter(_.userId === userId).result.headOption
  }

  def getTimezoneAction(userId: Long): DBIO[Option[UserTimezone]] = {
    userTimezonesTable.filter(_.userId === userId).result.headOption
  }

  def updateTimezone(userTimezone: UserTimezone): Future[Int] = {
    val action = for {
      existing <- userTimezonesTable.filter(_.userId === userTimezone.userId).result.headOption
      result <- existing match {
        case Some(_) =>
          // Update existing record
          userTimezonesTable.filter(_.userId === userTimezone.userId)
            .map(t => (t.timezone, t.updatedAt))
            .update((userTimezone.timezone, Instant.now()))
        case None =>
          // Insert new record
          userTimezonesTable += userTimezone
      }
    } yield result
    
    db.run(action.transactionally)
  }

  def updateTimezoneAction(userTimezone: UserTimezone): DBIO[Int] = {
    userTimezonesTable.filter(_.userId === userTimezone.userId)
      .map(t => (t.timezone, t.updatedAt))
      .update((userTimezone.timezone, Instant.now()))
  }

  def deleteTimezone(userId: Long): Future[Int] = db.run {
    userTimezonesTable.filter(_.userId === userId).delete
  }

  def deleteTimezoneAction(userId: Long): DBIO[Int] = {
    userTimezonesTable.filter(_.userId === userId).delete
  }

  // UserAvailability CRUD methods

  def createAvailabilityAction(userAvailability: UserAvailability): DBIO[Long] = {
    (userAvailabilityTable.map(a => (a.userId, a.fromWeekDay, a.toWeekDay, a.fromHourInclusive, a.toHourExclusive, a.availabilityStatus, a.createdAt, a.updatedAt))
      returning userAvailabilityTable.map(_.id)
      into ((_, id) => id)
    ) += (userAvailability.userId, userAvailability.fromWeekDay, userAvailability.toWeekDay, userAvailability.fromHourInclusive, userAvailability.toHourExclusive, userAvailability.availabilityStatus, userAvailability.createdAt, userAvailability.updatedAt)
  }

  def createAvailability(userAvailability: UserAvailability): Future[Long] = {
    db.run(createAvailabilityAction(userAvailability))
  }

  def getAvailabilityById(id: Long): Future[Option[UserAvailability]] = db.run {
    userAvailabilityTable.filter(_.id === id).result.headOption
  }

  def getAvailabilityByIdAction(id: Long): DBIO[Option[UserAvailability]] = {
    userAvailabilityTable.filter(_.id === id).result.headOption
  }

  def getAllAvailabilitiesByUserId(userId: Long): Future[Seq[UserAvailability]] = db.run {
    userAvailabilityTable.filter(_.userId === userId).result
  }

  def getAllAvailabilitiesByUserIdAction(userId: Long): DBIO[Seq[UserAvailability]] = {
    userAvailabilityTable.filter(_.userId === userId).result
  }

  def updateAvailability(userAvailability: UserAvailability): Future[Int] = db.run {
    userAvailabilityTable.filter(_.id === userAvailability.id)
      .map(a => (a.fromWeekDay, a.toWeekDay, a.fromHourInclusive, a.toHourExclusive, a.availabilityStatus, a.updatedAt))
      .update((userAvailability.fromWeekDay, userAvailability.toWeekDay, userAvailability.fromHourInclusive, userAvailability.toHourExclusive, userAvailability.availabilityStatus, Instant.now()))
  }

  def updateAvailabilityAction(userAvailability: UserAvailability): DBIO[Int] = {
    userAvailabilityTable.filter(_.id === userAvailability.id)
      .map(a => (a.fromWeekDay, a.toWeekDay, a.fromHourInclusive, a.toHourExclusive, a.availabilityStatus, a.updatedAt))
      .update((userAvailability.fromWeekDay, userAvailability.toWeekDay, userAvailability.fromHourInclusive, userAvailability.toHourExclusive, userAvailability.availabilityStatus, Instant.now()))
  }

  def deleteAvailability(id: Long): Future[Int] = db.run {
    userAvailabilityTable.filter(_.id === id).delete
  }

  def deleteAvailabilityAction(id: Long): DBIO[Int] = {
    userAvailabilityTable.filter(_.id === id).delete
  }
}
