package models

import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery
import java.time.Instant

trait OAuth2InfoComponent {
  self: YtUserComponent =>
  
  protected val profile: JdbcProfile
  import profile.api._

  // Implicit mapper for Instant

  class OAuth2InfoTable(tag: Tag) extends Table[OAuth2Info](tag, "oauth2_tokens") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userChannelId = column[String]("user_channel_id")
    def accessToken = column[String]("access_token")
    def tokenType = column[Option[String]]("token_type")
    def expiresIn = column[Option[Int]]("expires_in")
    def refreshToken = column[Option[String]]("refresh_token")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")
    
    def ytUserFk = foreignKey("fk_oauth2_tokens_yt_users", userChannelId, ytUsersTable)(_.userChannelId, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)
    
    def * = (
      id.?, 
      userChannelId, 
      accessToken, 
      tokenType, 
      expiresIn, 
      refreshToken, 
      createdAt, 
      updatedAt
    ) <> ((OAuth2Info.apply _).tupled, OAuth2Info.unapply)
  }

  val oauth2InfoTable = TableQuery[OAuth2InfoTable]
}