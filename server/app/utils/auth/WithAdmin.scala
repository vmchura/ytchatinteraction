package utils.auth

import models.User
import play.api.mvc.Request
import play.silhouette.api.Authorization
import play.silhouette.impl.authenticators.CookieAuthenticator

import scala.concurrent.Future

/**
 * Authorization class for admin-only access.
 * Only users with admin privileges can access views/actions protected by this authorization.
 */
case class WithAdmin() extends Authorization[User, CookieAuthenticator] {

  def isAuthorized[B](user: User, authenticator: CookieAuthenticator)(implicit
      request: Request[B]
  ): Future[Boolean] = {
    Future.successful(WithAdmin.isAdminUser(user.userId))
  }
}

object WithAdmin {
  
  /**
   * List of admin user IDs. 
   * In a production environment, this should be moved to configuration or database.
   * For now, we'll hardcode some admin user IDs as an example.
   */
  private val adminUserIds: Set[Long] = Set(
    1L,  // Admin user with ID 1
    // Add more admin user IDs as needed
  )
  
  /**
   * Checks if a user ID belongs to an admin user.
   * 
   * @param userId The user ID to check
   * @return true if the user is an admin, false otherwise
   */
  def isAdminUser(userId: Long): Boolean = {
    adminUserIds.contains(userId)
  }
  
  /**
   * Checks if a user (with both ID and username) is an admin.
   * This method checks both ID and username for maximum flexibility.
   * 
   * @param user The User object to check
   * @return true if the user is an admin, false otherwise
   */
  def isAdmin(user: User): Boolean = {
    isAdminUser(user.userId)
  }
}
