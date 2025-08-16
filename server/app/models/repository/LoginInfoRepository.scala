package models.repository

import models.component.{LoginInfoComponent, UserComponent}
import models.{LoginInfo, User}
import play.api.db.slick.DatabaseConfigProvider
import play.silhouette.api.LoginInfo as SilhouetteLoginInfo
import slick.jdbc.JdbcProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LoginInfoRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
  userRepository: UserRepository
)(implicit ec: ExecutionContext) extends LoginInfoComponent with UserComponent {
  
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig.*
  import profile.api.*
  
  /**
   * Saves login info.
   *
   * @param loginInfo The login info to save.
   * @return The saved login info.
   */
  def save(loginInfo: LoginInfo): Future[LoginInfo] = {
    val query = loginInfoTable.filter(info => 
      info.providerId === loginInfo.providerId && 
      info.providerKey === loginInfo.providerKey
    ).result.headOption.flatMap {
      case Some(existing) => 
        // Login info already exists, return it
        DBIO.successful(existing)
      case None => 
        // Login info doesn't exist, insert it
        (loginInfoTable returning loginInfoTable.map(_.id) into ((info, id) => info.copy(id = Some(id))))
          .+=(loginInfo)
    }
    
    db.run(query)
  }

  /**
   * Finds login info by Silhouette login info.
   *
   * @param loginInfo The Silhouette login info.
   * @return The found login info or None if no login info could be found.
   */
  def find(loginInfo: SilhouetteLoginInfo): Future[Option[LoginInfo]] = {
    db.run(
      loginInfoTable.filter(info => 
        info.providerId === loginInfo.providerID && 
        info.providerKey === loginInfo.providerKey
      ).result.headOption
    )
  }

  /**
   * Finds a user by login info.
   *
   * @param loginInfo The Silhouette login info.
   * @return The found user or None if no user could be found.
   */
  def findUser(loginInfo: SilhouetteLoginInfo): Future[Option[User]] = {
    val query = for {
      loginInfoOpt <- loginInfoTable.filter(info => 
        info.providerId === loginInfo.providerID && 
        info.providerKey === loginInfo.providerKey
      ).result.headOption
      
      userOpt <- loginInfoOpt match {
        case Some(info) => usersTable.filter(_.userId === info.userId).result.headOption
        case None => DBIO.successful(None)
      }
    } yield userOpt
    
    db.run(query)
  }

  /**
   * Finds all login info for a user.
   *
   * @param userId The user ID.
   * @return The list of login info.
   */
  def findForUser(userId: Long): Future[Seq[LoginInfo]] = {
    db.run(loginInfoTable.filter(_.userId === userId).result)
  }

  /**
   * Adds a new login info for a user.
   *
   * @param userId The user ID.
   * @param loginInfo The Silhouette login info.
   * @return The added login info.
   */
  def add(userId: Long, loginInfo: SilhouetteLoginInfo): Future[LoginInfo] = {
    val info = LoginInfo(
      providerId = loginInfo.providerID,
      providerKey = loginInfo.providerKey,
      userId = userId
    )
    
    save(info)
  }

  /**
   * Removes login info.
   *
   * @param loginInfo The Silhouette login info.
   * @return A future to wait for the process to be completed.
   */
  def remove(loginInfo: SilhouetteLoginInfo): Future[Unit] = {
    db.run(
      loginInfoTable.filter(info => 
        info.providerId === loginInfo.providerID && 
        info.providerKey === loginInfo.providerKey
      ).delete
    ).map(_ => ())
  }
}