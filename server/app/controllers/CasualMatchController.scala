package controllers

import scala.concurrent.ExecutionException
import play.api.mvc._
import javax.inject._

@Singleton
class CasualMatchController @Inject() (
  components: DefaultSilhouetteControllerComponents
  )(implicit ec: ExecutionException) extends SilhouetteController(components)
