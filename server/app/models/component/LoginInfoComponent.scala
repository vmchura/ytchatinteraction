package models.component

import models.LoginInfo
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

trait LoginInfoComponent {
  self: UserComponent =>
  
  protected val profile: JdbcProfile
  import profile.api.*

  class LoginInfoTable(tag: Tag) extends Table[LoginInfo](tag, "login_info") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def providerId = column[String]("provider_id")
    def providerKey = column[String]("provider_key")
    def userId = column[Long]("user_id")
    
    def userFk = foreignKey("fk_login_info_users", userId, usersTable)(_.userId, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    
    // Create a unique index on provider ID and provider key
    def uniqueProviderIndex = index("unique_login_info", (providerId, providerKey), unique = true)
    
    def * = (id.?, providerId, providerKey, userId) <> ((LoginInfo.apply _).tupled, LoginInfo.unapply)
  }

  val loginInfoTable = TableQuery[LoginInfoTable]
}