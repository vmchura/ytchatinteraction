-- !Ups

-- Create table for registered content creator YouTube channels (admin only)
CREATE TABLE content_creator_channels (
  id SERIAL NOT NULL PRIMARY KEY,
  youtube_channel_id VARCHAR(24) NOT NULL UNIQUE,
  youtube_channel_name VARCHAR NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for faster lookups by channel ID
CREATE INDEX idx_content_creator_channels_youtube_channel_id ON content_creator_channels (youtube_channel_id);

-- Create index for filtering active channels
CREATE INDEX idx_content_creator_channels_active ON content_creator_channels (is_active);

-- !Downs

DROP INDEX IF EXISTS idx_content_creator_channels_active;
DROP INDEX IF EXISTS idx_content_creator_channels_youtube_channel_id;
DROP TABLE content_creator_channels;