package models

import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import play.silhouette.api.LoginInfo
import play.silhouette.persistence.daos.DelegableAuthInfoDAO

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

@Singleton
class OAuth2InfoRepository @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                     ytUserRepository: YtUserRepository)(implicit ec: ExecutionContext, val classTag: ClassTag[play.silhouette.impl.providers.OAuth2Info]) extends OAuth2InfoComponent
with YtUserComponent
with UserComponent
with DelegableAuthInfoDAO[play.silhouette.impl.providers.OAuth2Info] {

  val dbConfig = dbConfigProvider.get[JdbcProfile]
  override protected val profile = dbConfig.profile
  import dbConfig._
  import profile.api._

  /**
   * Finds the OAuth2 info for the specified login info.
   *
   * @param loginInfo The login info for which the OAuth2 info should be found.
   * @return The found OAuth2 info or None if no OAuth2 info could be found for the given login info.
   */
  def find(loginInfo: LoginInfo): Future[Option[play.silhouette.impl.providers.OAuth2Info]] = {
    // First, get the YtUser that matches this login info (YouTube provider)
    if (loginInfo.providerID == "youtube") {
      val channelId = loginInfo.providerKey

      val query = for {
        oAuth2Info <- oauth2InfoTable.filter(_.userChannelId === channelId).result.headOption
      } yield oAuth2Info.map(_.toSilhouetteOAuth2Info)

      db.run(query)
    } else {
      Future.successful(None)
    }
  }

  /**
   * Adds new OAuth2 info for the specified login info.
   *
   * @param loginInfo The login info for which the OAuth2 info should be added.
   * @param authInfo  The OAuth2 info to add.
   * @return The added OAuth2 info.
   */
  def add(loginInfo: LoginInfo, authInfo: play.silhouette.impl.providers.OAuth2Info): Future[play.silhouette.impl.providers.OAuth2Info] = {
    if (loginInfo.providerID == "youtube") {
      val channelId = loginInfo.providerKey
      val now = Instant.now()

      val dbOAuth2Info = OAuth2Info(
        userChannelId = channelId,
        accessToken = authInfo.accessToken,
        tokenType = authInfo.tokenType,
        expiresIn = authInfo.expiresIn,
        refreshToken = authInfo.refreshToken,
        createdAt = now,
        updatedAt = now
      )

      val query = for {
        // Check if the YtUser exists
        ytUserOpt <- ytUsersTable.filter(_.userChannelId === channelId).result.headOption

        result <- ytUserOpt match {
          case Some(_) =>
            // Insert the OAuth2 info and get back only the id
            (oauth2InfoTable returning oauth2InfoTable.map(_.id))
              .+=(dbOAuth2Info)
              .map(id => dbOAuth2Info.copy(id = Some(id)).toSilhouetteOAuth2Info)

          case None =>
            // YtUser not found
            DBIO.failed(new Exception(s"YtUser with channel ID $channelId not found"))
        }
      } yield result

      db.run(query)
    } else {
      Future.failed(new Exception(s"Provider ${loginInfo.providerID} not supported"))
    }
  }

  /**
   * Updates the OAuth2 info for the specified login info.
   *
   * @param loginInfo The login info for which the OAuth2 info should be updated.
   * @param authInfo  The OAuth2 info to update.
   * @return The updated OAuth2 info.
   */
  def update(loginInfo: LoginInfo, authInfo: play.silhouette.impl.providers.OAuth2Info): Future[play.silhouette.impl.providers.OAuth2Info] = {
    if (loginInfo.providerID == "youtube") {
      val channelId = loginInfo.providerKey
      val now = Instant.now()

      val query = for {
        // Get the existing OAuth2 info
        existing <- oauth2InfoTable.filter(_.userChannelId === channelId).result.headOption

        result <- existing match {
          case Some(info) =>
            // Update the existing record
            val updated = info.copy(
              accessToken = authInfo.accessToken,
              tokenType = authInfo.tokenType,
              expiresIn = authInfo.expiresIn,
              refreshToken = authInfo.refreshToken.orElse(info.refreshToken), // Keep old refresh token if new one is not provided
              updatedAt = now
            )

            oauth2InfoTable.filter(_.id === info.id).update(updated).map(_ => updated.toSilhouetteOAuth2Info)

          case None =>
            // OAuth2 info not found
            DBIO.failed(new Exception(s"OAuth2Info for channel ID $channelId not found"))
        }
      } yield result

      db.run(query)
    } else {
      Future.failed(new Exception(s"Provider ${loginInfo.providerID} not supported"))
    }
  }

  /**
   * Saves the OAuth2 info for the specified login info.
   * This either adds the OAuth2 info if it doesn't exist or updates it if it already exists.
   *
   * @param loginInfo The login info for which the OAuth2 info should be saved.
   * @param authInfo  The OAuth2 info to save.
   * @return The saved OAuth2 info.
   */
  def save(loginInfo: LoginInfo, authInfo: play.silhouette.impl.providers.OAuth2Info): Future[play.silhouette.impl.providers.OAuth2Info] = {
    find(loginInfo).flatMap {
      case Some(_) => update(loginInfo, authInfo)
      case None => add(loginInfo, authInfo)
    }
  }

  /**
   * Removes the OAuth2 info for the specified login info.
   *
   * @param loginInfo The login info for which the OAuth2 info should be removed.
   * @return A future to wait for the process to be completed.
   */
  def remove(loginInfo: LoginInfo): Future[Unit] = {
    if (loginInfo.providerID == "youtube") {
      val channelId = loginInfo.providerKey

      db.run(oauth2InfoTable.filter(_.userChannelId === channelId).delete).map(_ => ())
    } else {
      Future.successful(())
    }
  }

  /**
   * Gets all OAuth2 info for a YouTube channel.
   *
   * @param channelId The YouTube channel ID.
   * @return The OAuth2 info.
   */
  def getByChannelId(channelId: String): Future[Option[OAuth2Info]] = {
    db.run(oauth2InfoTable.filter(_.userChannelId === channelId).result.headOption)
  }
}