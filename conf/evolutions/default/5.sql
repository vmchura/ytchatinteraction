-- !Ups

-- Create table for tracking changes to user_streamer_state table
CREATE TABLE user_streamer_state_log (
  log_id SERIAL PRIMARY KEY,
  user_id BIGINT,
  channel_id VARCHAR(24),
  event_id INT NOT NULL,
  currency_transferred_amount INT NOT NULL,
  log_type VARCHAR NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_streamer_state_log_user
    FOREIGN KEY (user_id)
    REFERENCES users (user_id),
  CONSTRAINT fk_user_streamer_state_log_event
    FOREIGN KEY (event_id)
    REFERENCES streamer_events (event_id),
  CONSTRAINT fk_user_streamer_state_log_channel
    FOREIGN KEY (channel_id)
    REFERENCES yt_users (user_channel_id)
);

-- !Downs
DROP TABLE IF EXISTS user_streamer_state_log;
