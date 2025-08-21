package services

import play.api.libs.ws.WSClient
import play.api.Configuration
import play.api.libs.json._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

/**
 * Service for fetching YouTube channel information from URLs using YouTube API.
 */
@Singleton
class YouTubeChannelInfoService @Inject()(
                                           ws: WSClient,
                                           config: Configuration
                                         )(implicit ec: ExecutionContext) {

  private val apiKey = config.get[String]("youtube.api.key")
  private val baseUrl = "https://www.googleapis.com/youtube/v3"

  // Regex patterns for different YouTube URL formats
  private val channelUrlPatterns = List(
    """https?://(?:www\.)?youtube\.com/@([^/?&]+)""".r,
    """https?://(?:www\.)?youtube\.com/channel/([^/?&]+)""".r,
    """https?://(?:www\.)?youtube\.com/c/([^/?&]+)""".r,
    """https?://(?:www\.)?youtube\.com/user/([^/?&]+)""".r
  )

  case class ChannelInfo(id: String, name: String)

  /**
   * Extracts channel information from a YouTube URL.
   */
  def getChannelInfoFromUrl(url: String): Future[Either[String, ChannelInfo]] = {
    extractChannelIdentifier(url) match {
      case Some((identifier, urlType)) =>
        urlType match {
          case "channel" =>
            // Direct channel ID - fetch channel info
            getChannelById(identifier)
          case "handle" =>
            // Handle (@username) - try forHandle parameter first
            getChannelByHandle(identifier)
          case "custom" | "user" =>
            // Need to search for the channel
            searchChannelByName(identifier)
        }
      case None =>
        Future.successful(Left("Invalid YouTube URL format. Please use a URL like: https://www.youtube.com/@ChannelName"))
    }
  }

  /**
   * Extracts the channel identifier and type from a YouTube URL.
   */
  private def extractChannelIdentifier(url: String): Option[(String, String)] = {
    val trimmedUrl = url.trim

    // Try each pattern sequentially
    channelUrlPatterns.head.findFirstMatchIn(trimmedUrl) match {
      case Some(m) => Some((m.group(1), "handle"))      // @username
      case None =>
        channelUrlPatterns(1).findFirstMatchIn(trimmedUrl) match {
          case Some(m) => Some((m.group(1), "channel"))   // /channel/UC...
          case None =>
            channelUrlPatterns(2).findFirstMatchIn(trimmedUrl) match {
              case Some(m) => Some((m.group(1), "custom"))  // /c/customname
              case None =>
                channelUrlPatterns(3).findFirstMatchIn(trimmedUrl) match {
                  case Some(m) => Some((m.group(1), "user")) // /user/username
                  case None => None
                }
            }
        }
    }
  }

  /**
   * Gets channel information by channel ID.
   */
  private def getChannelById(channelId: String): Future[Either[String, ChannelInfo]] = {
    ws.url(s"$baseUrl/channels")
      .withQueryStringParameters(
        "key" -> apiKey,
        "part" -> "snippet",
        "id" -> channelId
      )
      .get()
      .map { response =>
        response.status match {
          case 200 =>
            val json = response.json
            val items = (json \ "items").as[JsArray]
            if (items.value.nonEmpty) {
              val item = items.value.head
              val id = (item \ "id").as[String]
              val name = (item \ "snippet" \ "title").as[String]
              Right(ChannelInfo(id, name))
            } else {
              Left("Channel not found")
            }
          case 403 =>
            Left("YouTube API quota exceeded or invalid API key")
          case _ =>
            Left(s"YouTube API error: ${response.status}")
        }
      }
      .recover {
        case ex => Left(s"Error fetching channel info: ${ex.getMessage}")
      }
  }

  /**
   * Gets channel by handle using YouTube API v3 forHandle parameter.
   */
  private def getChannelByHandle(handle: String): Future[Either[String, ChannelInfo]] = {
    ws.url(s"$baseUrl/channels")
      .withQueryStringParameters(
        "key" -> apiKey,
        "part" -> "snippet",
        "forHandle" -> handle
      )
      .get()
      .flatMap { response =>
        response.status match {
          case 200 =>
            val json = response.json
            val items = (json \ "items").as[JsArray]
            if (items.value.nonEmpty) {
              val item = items.value.head
              val id = (item \ "id").as[String]
              val name = (item \ "snippet" \ "title").as[String]
              Future.successful(Right(ChannelInfo(id, name)))
            } else {
              // Fallback to search if handle lookup fails
              searchChannelByName(handle)
            }
          case _ =>
            // Fallback to search if handle lookup fails
            searchChannelByName(handle)
        }
      }
      .recover {
        case _ => Left(s"Could not find channel with handle: @$handle")
      }
  }

  /**
   * Searches for a channel by name using YouTube search API.
   */
  private def searchChannelByName(name: String): Future[Either[String, ChannelInfo]] = {
    ws.url(s"$baseUrl/search")
      .withQueryStringParameters(
        "key" -> apiKey,
        "part" -> "snippet",
        "q" -> name,
        "type" -> "channel",
        "maxResults" -> "1"
      )
      .get()
      .map { response =>
        response.status match {
          case 200 =>
            val json = response.json
            val items = (json \ "items").as[JsArray]
            if (items.value.nonEmpty) {
              val item = items.value.head
              val channelId = (item \ "snippet" \ "channelId").as[String]
              val channelTitle = (item \ "snippet" \ "channelTitle").as[String]
              Right(ChannelInfo(channelId, channelTitle))
            } else {
              Left(s"No channel found with name: $name")
            }
          case 403 =>
            Left("YouTube API quota exceeded or invalid API key")
          case _ =>
            Left(s"YouTube search API error: ${response.status}")
        }
      }
      .recover {
        case ex => Left(s"Error searching for channel: ${ex.getMessage}")
      }
  }
}