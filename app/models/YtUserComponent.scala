package models

import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery
import java.time.Instant

trait YtUserComponent {
  self: UserComponent =>
  
  protected val profile: JdbcProfile
  import profile.api._

  // Implicit mapper for Instant
  given BaseColumnType[Instant] = MappedColumnType.base[Instant, java.sql.Timestamp](
    instant => java.sql.Timestamp.from(instant),
    timestamp => timestamp.toInstant
  )

  class YtUsersTable(tag: Tag) extends Table[YtUser](tag, "yt_users") {
    def userChannelId = column[String]("user_channel_id", O.PrimaryKey)
    def userId = column[Long]("user_id")
    def displayName = column[Option[String]]("display_name")
    def email = column[Option[String]]("email")
    def profileImageUrl = column[Option[String]]("profile_image_url")
    def activated = column[Boolean]("activated", O.Default(false))
    def createdAt = column[Instant]("created_at", O.Default(Instant.now()))
    def updatedAt = column[Instant]("updated_at", O.Default(Instant.now()))
    
    def userFk = foreignKey("fk_yt_users_with_users", userId, usersTable)(_.userId)
    
    def * = (
      userChannelId, 
      userId, 
      displayName, 
      email, 
      profileImageUrl, 
      activated, 
      createdAt, 
      updatedAt
    ) <> ((YtUser.apply _).tupled, YtUser.unapply)
  }

  val ytUsersTable = TableQuery[YtUsersTable]
}