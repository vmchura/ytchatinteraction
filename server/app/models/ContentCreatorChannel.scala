package models

import play.api.libs.json._
import java.time.Instant

/**
 * Content Creator Channel model for admin-registered YouTube channels.
 *
 * @param id                 The unique ID of the content creator channel record.
 * @param youtubeChannelId   The YouTube channel ID (24 characters).
 * @param youtubeChannelName The display name of the YouTube channel.
 * @param isActive           Whether this channel is currently active.
 * @param updatedAt          When this record was last updated.
 */
case class ContentCreatorChannel(
                                  id: Option[Long] = None,
                                  youtubeChannelId: String,
                                  youtubeChannelName: String,
                                  isActive: Boolean = true,
                                  updatedAt: Instant = Instant.now()
                                )
