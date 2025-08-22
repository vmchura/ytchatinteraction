-- !Ups

-- Add content creator channel ID to tournaments table
ALTER TABLE tournaments
ADD COLUMN content_creator_channel_id BIGINT;

ALTER TABLE tournaments
ADD COLUMN challonge_url VARCHAR(255);

-- Add foreign key constraint to content_creator_channels table
ALTER TABLE tournaments
ADD CONSTRAINT fk_tournaments_content_creator_channels
FOREIGN KEY (content_creator_channel_id)
REFERENCES content_creator_channels (id)
ON DELETE SET NULL;

-- Create index for faster lookups
CREATE INDEX idx_tournaments_content_creator_channel_id ON tournaments (content_creator_channel_id);

-- !Downs

DROP INDEX IF EXISTS idx_tournaments_content_creator_channel_id;
ALTER TABLE tournaments DROP CONSTRAINT IF EXISTS fk_tournaments_content_creator_channels;
ALTER TABLE tournaments DROP COLUMN IF EXISTS content_creator_channel_id;
ALTER TABLE tournaments DROP COLUMN IF EXISTS challonge_url;