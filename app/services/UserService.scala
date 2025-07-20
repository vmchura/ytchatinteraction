package services

import models.repository.{LoginInfoRepository, UserRepository, YtUserRepository}
import javax.inject.Inject
import models.User
import play.silhouette.api.LoginInfo
import play.silhouette.api.services.IdentityService

import scala.concurrent.{ExecutionContext, Future}

/**
 * Service interface for User operations.
 */
trait UserService extends IdentityService[User] {

  /**
   * Retrieves a user that matches the specified login info.
   *
   * @param loginInfo The login info to retrieve a user.
   * @return The retrieved user or None if no user could be retrieved for the given login info.
   */
  def retrieve(loginInfo: LoginInfo): Future[Option[User]]

  /**
   * Saves a user.
   *
   * @param user The user to save.
   * @return The saved user.
   */
  def save(user: User): Future[User]

  /**
   * Creates the link between a user and a login info.
   *
   * @param user The user to link.
   * @param loginInfo The login info to link with the user.
   * @return The updated user.
   */
  def link(user: User, loginInfo: LoginInfo): Future[User]

  /**
   * Removes the link between a user and a login info.
   *
   * @param user The user to unlink.
   * @param loginInfo The login info to unlink from the user.
   * @return The updated user.
   */
  def unlink(user: User, loginInfo: LoginInfo): Future[User]

  /**
   * Gets all users in the system.
   *
   * @return A list of all users.
   */
  def getAllUsers(): Future[Seq[User]]
}

/**
 * Handles actions to users.
 *
 * @param userRepository The user repository implementation.
 * @param loginInfoRepository The login info repository implementation.
 * @param ytUserRepository The YouTube user repository implementation.
 * @param ec The execution context.
 */
class UserServiceImpl @Inject() (
  userRepository: UserRepository,
  loginInfoRepository: LoginInfoRepository,
  ytUserRepository: YtUserRepository
)(implicit ec: ExecutionContext) extends UserService {

  /**
   * Retrieves a user that matches the specified login info.
   *
   * @param loginInfo The login info to retrieve a user.
   * @return The retrieved user or None if no user could be retrieved for the given login info.
   */
  override def retrieve(loginInfo: LoginInfo): Future[Option[User]] = {
    loginInfoRepository.findUser(loginInfo)
  }

  /**
   * Saves a user.
   *
   * @param user The user to save.
   * @return The saved user.
   */
  override def save(user: User): Future[User] = {
    userRepository.create(user.userName)
  }

  /**
   * Creates the link between a user and a login info.
   *
   * @param user The user to link.
   * @param loginInfo The login info to link with the user.
   * @return The updated user.
   */
  override def link(user: User, loginInfo: LoginInfo): Future[User] = {
    loginInfoRepository.add(user.userId, loginInfo).map(_ => user)
  }

  /**
   * Removes the link between a user and a login info.
   *
   * @param user The user to unlink.
   * @param loginInfo The login info to unlink from the user.
   * @return The updated user.
   */
  override def unlink(user: User, loginInfo: LoginInfo): Future[User] = {
    loginInfoRepository.remove(loginInfo).map(_ => user)
  }

  /**
   * Gets all users in the system.
   *
   * @return A list of all users.
   */
  override def getAllUsers(): Future[Seq[User]] = {
    userRepository.list()
  }
}
