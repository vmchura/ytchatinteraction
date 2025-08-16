package models

import play.silhouette.api.{Identity, LoginInfo}
import play.api.libs.json._

/**
 * The user entity.
 *
 * @param userId The user ID.
 * @param userName The username.
 */
case class User(userId: Long, userName: String) extends Identity {
  /**
   * Gets a login info for this user based on the provided provider ID.
   *
   * This helper method simplifies getting a LoginInfo for Silhouette when we need it.
   * For YouTube OAuth2, the provider ID would be "youtube" and the provider key would be 
   * the YouTube channel ID, which we can get from a related YtUser.
   *
   * @param providerId The provider ID (e.g., "youtube").
   * @param providerKey The provider key (e.g., YouTube channel ID).
   * @return A LoginInfo instance.
   */
  def loginInfo(providerId: String, providerKey: String): LoginInfo = 
    LoginInfo(providerId, providerKey)
}

object User {
  implicit val userFormat: OFormat[User] = Json.format[User]
}