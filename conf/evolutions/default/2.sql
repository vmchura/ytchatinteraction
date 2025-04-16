# --- !Ups

-- Table for streamer events
CREATE TABLE "streamer_events" (
  "event_id" SERIAL PRIMARY KEY,
  "channel_id" VARCHAR NOT NULL,
  "event_name" VARCHAR NOT NULL,
  "event_description" TEXT,
  "event_type" VARCHAR NOT NULL, -- 'OFFLINE', 'LIVE', 'SCHEDULED'
  "is_active" BOOLEAN DEFAULT TRUE,
  "start_time" TIMESTAMP NOT NULL,
  "end_time" TIMESTAMP,
  "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_streamer_events_channel
    FOREIGN KEY ("channel_id")
    REFERENCES "yt_streamers" ("channel_id")
    ON DELETE CASCADE
);

-- Table for event polls
CREATE TABLE "event_polls" (
  "poll_id" SERIAL PRIMARY KEY,
  "event_id" INTEGER NOT NULL,
  "poll_question" VARCHAR NOT NULL,
  CONSTRAINT fk_event_polls_event
    FOREIGN KEY ("event_id")
    REFERENCES "streamer_events" ("event_id")
    ON DELETE CASCADE
);

-- Table for poll options
CREATE TABLE "poll_options" (
  "option_id" SERIAL PRIMARY KEY,
  "poll_id" INTEGER NOT NULL,
  "option_text" VARCHAR NOT NULL,
  CONSTRAINT fk_poll_options_poll
    FOREIGN KEY ("poll_id")
    REFERENCES "event_polls" ("poll_id")
    ON DELETE CASCADE
);

-- Table for poll votes
CREATE TABLE "poll_votes" (
  "vote_id" SERIAL PRIMARY KEY,
  "poll_id" INTEGER NOT NULL,
  "option_id" INTEGER NOT NULL,
  "user_id" BIGINT NOT NULL,
  "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  "confidence_amount" BIGINT NOT NULL,
  "message_by_chat" VARCHAR,
  CONSTRAINT fk_poll_votes_poll
    FOREIGN KEY ("poll_id")
    REFERENCES "event_polls" ("poll_id")
    ON DELETE CASCADE,
  CONSTRAINT fk_poll_votes_option
    FOREIGN KEY ("option_id")
    REFERENCES "poll_options" ("option_id")
    ON DELETE CASCADE,
  CONSTRAINT fk_poll_votes_user
    FOREIGN KEY ("user_id")
    REFERENCES "users" ("user_id")
    ON DELETE CASCADE
);

-- Indexes for faster lookups
CREATE INDEX idx_streamer_events_channel_id ON "streamer_events"("channel_id");
CREATE INDEX idx_streamer_events_is_active ON "streamer_events"("is_active");
CREATE INDEX idx_event_polls_event_id ON "event_polls"("event_id");
CREATE INDEX idx_event_polls_is_active ON "event_polls"("is_active");
CREATE INDEX idx_poll_options_poll_id ON "poll_options"("poll_id");
CREATE INDEX idx_poll_votes_poll_id ON "poll_votes"("poll_id");
CREATE INDEX idx_poll_votes_option_id ON "poll_votes"("option_id");
CREATE INDEX idx_poll_votes_user_id ON "poll_votes"("user_id");

# --- !Downs

DROP TABLE IF EXISTS "poll_votes";
DROP TABLE IF EXISTS "poll_options";
DROP TABLE IF EXISTS "event_polls";
DROP TABLE IF EXISTS "streamer_events";
