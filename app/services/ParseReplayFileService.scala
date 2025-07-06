package services

import scala.concurrent.Future
import java.io.File

/**
 * Service for parsing replay files
 */
trait ParseReplayFileService {
  def parseFile(file: File): Future[Either[String, String]]
}

/**
 * Implementation that delegates to the external replay parser service
 */
class DefaultParseReplayFileService extends ParseReplayFileService {
  
  override def parseFile(file: File): Future[Either[String, String]] = {
    // This would be implemented to call the external service
    // For now, return a mock implementation
    Future.successful(Left("Not implemented"))
  }
}
