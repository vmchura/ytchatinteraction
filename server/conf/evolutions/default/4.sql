-- !Ups

-- Create the youtube_chat_messages table
CREATE TABLE youtube_chat_messages (
  message_id SERIAL PRIMARY KEY,
  live_chat_id VARCHAR(255) NOT NULL,
  channel_id VARCHAR(255) NOT NULL,
  raw_message TEXT NOT NULL, -- Use JSONB for better performance with JSON data
  author_channel_id VARCHAR(255) NOT NULL,
  author_display_name VARCHAR(255) NOT NULL,
  message_text TEXT NOT NULL,
  published_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create indexes for faster queries
CREATE INDEX idx_youtube_chat_messages_live_chat_id ON youtube_chat_messages(live_chat_id);
CREATE INDEX idx_youtube_chat_messages_channel_id ON youtube_chat_messages(channel_id);
CREATE INDEX idx_youtube_chat_messages_author_channel_id ON youtube_chat_messages(author_channel_id);
CREATE INDEX idx_youtube_chat_messages_published_at ON youtube_chat_messages(published_at);

-- !Downs

DROP TABLE youtube_chat_messages;
