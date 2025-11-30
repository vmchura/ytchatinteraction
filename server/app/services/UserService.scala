package services

import models.repository.{LoginInfoRepository, UserRepository, YtUserRepository, UserAliasRepository}
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
   * Creates a user with a randomly generated StarCraft alias.
   *
   * @param userName The original username.
   * @return The created user with alias.
   */
  def createUserWithAlias(): Future[User]

  /**
   * Creates the link between a user and a login info.
   *
   * @param user      The user to link.
   * @param loginInfo The login info to link with the user.
   * @return The updated user.
   */
  def link(user: User, loginInfo: LoginInfo): Future[User]

  /**
   * Removes the link between a user and a login info.
   *
   * @param user      The user to unlink.
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

  /**
   * Updates a user's alias.
   *
   * @param userId   The ID of the user to update.
   * @param newAlias The new alias for the user.
   * @return A Future containing the number of rows affected (should be 1 if successful).
   */
  def updateUserAlias(userId: Long, newAlias: String): Future[Int]

  /**
   * Checks if a user can use a specific alias.
   * An alias can be used if it's either new or was previously used by the same user.
   *
   * @param userId The ID of the user.
   * @param alias  The alias to check.
   * @return A Future containing true if the user can use this alias.
   */
  def canUserUseAlias(userId: Long, alias: String): Future[Boolean]

  def hasSingleAliasHistory(userId: Long): Future[Boolean]
}

/**
 * Handles actions to users.
 *
 * @param userRepository      The user repository implementation.
 * @param loginInfoRepository The login info repository implementation.
 * @param ytUserRepository    The YouTube user repository implementation.
 * @param userAliasRepository The user alias repository implementation.
 * @param ec                  The execution context.
 */
class UserServiceImpl @Inject()(
                                 userRepository: UserRepository,
                                 loginInfoRepository: LoginInfoRepository,
                                 ytUserRepository: YtUserRepository,
                                 userAliasRepository: UserAliasRepository
                               )(implicit ec: ExecutionContext) extends UserService {

  private val alias_prefix: Array[String] = Array("Zealot", "Dragoon", "HighTemplar", "DarkTemplar", "Archon", "DarkArchon",
    "Scout", "Corsair", "Carrier", "Arbiter", "Reaver",
    "Marine", "Firebat", "Ghost", "Vulture", "Tank", "Goliath", "Wraith", "Battlecruiser", "ScienceVessel",
    "Zergling", "Hydralisk", "Lurker", "Ultralisk", "Mutalisk", "Guardian", "Devourer", "Defiler")

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
   * Creates a user with a randomly generated StarCraft alias.
   *
   * @return The created user with alias.
   */
  override def createUserWithAlias(): Future[User] = {
    val alias = s"${alias_prefix(scala.util.Random.nextInt(alias_prefix.length))}-${String.format("%08d", scala.util.Random.nextInt(99999999) + 1)}"
    userRepository.createWithAlias(alias)
  }

  /**
   * Creates the link between a user and a login info.
   *
   * @param user      The user to link.
   * @param loginInfo The login info to link with the user.
   * @return The updated user.
   */
  override def link(user: User, loginInfo: LoginInfo): Future[User] = {
    loginInfoRepository.add(user.userId, loginInfo).map(_ => user)
  }

  /**
   * Removes the link between a user and a login info.
   *
   * @param user      The user to unlink.
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

  /**
   * Updates a user's alias.
   *
   * @param userId   The ID of the user to update.
   * @param newAlias The new alias for the user.
   * @return A Future containing the number of rows affected (should be 1 if successful).
   */
  override def updateUserAlias(userId: Long, newAlias: String): Future[Int] = {
    for {
      // First verify the user exists
      userExists <- userRepository.exists(userId)
      result <- if (userExists) {
        // Attempt to change the alias
        userAliasRepository.changeUserAlias(userId, newAlias.trim).map {
          case true => 1 // Success
          case false => 0 // Failed (alias not available for this user)
        }
      } else {
        Future.successful(0) // User doesn't exist
      }
    } yield result
  }

  /**
   * Checks if a user can use a specific alias.
   * An alias can be used if it's either new or was previously used by the same user.
   *
   * @param userId The ID of the user.
   * @param alias  The alias to check.
   * @return A Future containing true if the user can use this alias.
   */
  override def canUserUseAlias(userId: Long, alias: String): Future[Boolean] = {
    userAliasRepository.canUserUseAlias(userId, alias.trim)
  }

  def hasSingleAliasHistory(userId: Long): Future[Boolean] = {
    userAliasRepository.getUserAliasHistory(userId).map(r => r.length == 1).recover(_ => false)
  }
}
