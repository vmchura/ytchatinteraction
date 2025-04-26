package services

import javax.inject.{Inject, Singleton}
import scala.collection.mutable
@Singleton
class ActiveLiveStream  @Inject()() {
  val activeStreams: mutable.HashMap[String, String] = mutable.HashMap()
  def addElement(liveChatID: String, streamTitle: String): Unit = {
    activeStreams.addOne(liveChatID -> streamTitle)
  }
  def getStreamTitle(liveChatID: String): String = {
    activeStreams.getOrElse(liveChatID, "Unknown Title")
  }
  def list(): Seq[String] = activeStreams.values.toSeq
}
