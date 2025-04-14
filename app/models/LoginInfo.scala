package models

import play.api.libs.json._
import play.silhouette.api.{LoginInfo => SilhouetteLoginInfo}

/**
 * A model representing login information.
 *
 * @param id The unique ID.
 * @param providerId The ID of the provider this info belongs to.
 * @param providerKey The key of the provider.
 * @param userId The user ID associated with this login info.
 */
case class LoginInfo(
  id: Option[Long] = None,
  providerId: String,
  providerKey: String,
  userId: Long
) {
  /**
   * Converts to a Silhouette login info.
   * 
   * @return A Silhouette login info instance.
   */
  def toSilhouette: SilhouetteLoginInfo = SilhouetteLoginInfo(providerId, providerKey)
}

object LoginInfo {
  implicit val loginInfoFormat: OFormat[LoginInfo] = Json.format[LoginInfo]
  
  /**
   * Creates a model login info from a Silhouette login info.
   *
   * @param silhouetteLoginInfo The Silhouette login info.
   * @param userId The user ID.
   * @return A model login info.
   */
  def fromSilhouette(silhouetteLoginInfo: SilhouetteLoginInfo, userId: Long): LoginInfo = {
    LoginInfo(
      providerId = silhouetteLoginInfo.providerID,
      providerKey = silhouetteLoginInfo.providerKey,
      userId = userId
    )
  }
}