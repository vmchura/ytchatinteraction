package utils

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

/**
 * Utility class to handle Dokku's DATABASE_URL format for Slick.
 * Dokku provides a URL like postgres://username:password@host:port/database
 * but Slick expects different configuration properties.
 */
object DatabaseUrlParser {
  
  /**
   * Parse DATABASE_URL environment variable and convert to Slick configuration
   * @return Updated Config with proper Slick database settings
   */
  def parseDbUrl(): Config = {
    val config = ConfigFactory.load()
    
    // Check if DATABASE_URL environment variable exists
    Option(System.getenv("DATABASE_URL")).map { dbUrl =>
      // Parse the DATABASE_URL format: postgres://username:password@host:port/database
      val dbUri = new java.net.URI(dbUrl)
      
      val dbUser = dbUri.getUserInfo.split(":")(0)
      val dbPassword = dbUri.getUserInfo.split(":")(1)
      val dbHost = dbUri.getHost
      val dbPort = dbUri.getPort
      val dbName = dbUri.getPath.substring(1)
      
      // Create JDBC URL in the format Slick expects
      val jdbcUrl = s"jdbc:postgresql://$dbHost:$dbPort/$dbName"
      
      // Update config with the parsed values
      config
        .withValue("slick.dbs.default.db.properties.url", ConfigValueFactory.fromAnyRef(jdbcUrl))
        .withValue("slick.dbs.default.db.properties.user", ConfigValueFactory.fromAnyRef(dbUser))
        .withValue("slick.dbs.default.db.properties.password", ConfigValueFactory.fromAnyRef(dbPassword))
    }.getOrElse(config) // Return original config if DATABASE_URL is not set
  }
}
