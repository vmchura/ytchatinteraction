package modules

import play.api.{ApplicationLoader, Configuration, Environment}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}
import utils.DatabaseUrlParser

/**
 * Custom application loader that handles DATABASE_URL parsing for Dokku deployments
 */
class DokkuApplicationLoader extends GuiceApplicationLoader {
  
  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder = {
    // Parse DATABASE_URL from environment variables (if present)
    val parsedConfig = DatabaseUrlParser.parseDbUrl()
    
    // Create a new context with the parsed configuration
    val updatedContext = context.copy(
      initialConfiguration = Configuration(parsedConfig)
    )
    
    // Pass the updated context to the standard GuiceApplicationLoader
    super.builder(updatedContext)
  }
}
