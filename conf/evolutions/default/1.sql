-- !Ups

CREATE TABLE people (
  id SERIAL PRIMARY KEY  NOT NULL,
  name VARCHAR NOT NULL,
  age INT NOT NULL
);

CREATE TABLE users (
user_id SERIAL NOT NULL PRIMARY KEY,
user_name VARCHAR NOT NULL);

CREATE TABLE yt_users (
  user_channel_id VARCHAR(24) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  display_name VARCHAR,
  email VARCHAR,
  profile_image_url VARCHAR,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  activated BOOLEAN DEFAULT FALSE,
  CONSTRAINT fk_yt_users_with_users
          FOREIGN KEY (user_id)
              REFERENCES users (user_id)
);

CREATE TABLE yt_streamer (
    channel_id VARCHAR(24) NOT NULL PRIMARY KEY,
    onwer_user_id BIGINT NOT NULL,
    current_balance_number BIGINT NOT NULL,
    CONSTRAINT fk_yt_streamer_with_users
              FOREIGN KEY (onwer_user_id)
                  REFERENCES users (user_id)
);

CREATE TABLE user_streamer_state(
    user_id BIGINT NOT NULL,
    streamer_channel_id VARCHAR(24) NOT NULL,
    current_balance_number BIGINT NOT NULL,
     CONSTRAINT fk_user_streamer_state_with_users
                   FOREIGN KEY (user_id)
                       REFERENCES users (user_id),
   CONSTRAINT fk_user_streamer_state_with_streamer
                      FOREIGN KEY (streamer_channel_id)
                          REFERENCES yt_streamer (channel_id)
);

-- CREATE TABLE FOR OAUTH2 TOKENS
CREATE TABLE oauth2_tokens (
  id SERIAL NOT NULL PRIMARY KEY,
  user_channel_id VARCHAR(24) NOT NULL,
  access_token VARCHAR NOT NULL,
  token_type VARCHAR,
  expires_in INTEGER,
  refresh_token VARCHAR,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_oauth2_tokens_yt_users FOREIGN KEY (user_channel_id) REFERENCES yt_users (user_channel_id) ON DELETE CASCADE
);

-- CREATE INDEX FOR FASTER TOKEN LOOKUPS
CREATE INDEX idx_oauth2_tokens_user_channel_id ON oauth2_tokens (user_channel_id);

-- CREATE TABLE FOR SILHOUETTE LOGIN INFO MAPPING
CREATE TABLE login_info (
  id SERIAL NOT NULL PRIMARY KEY,
  provider_id VARCHAR NOT NULL,
  provider_key VARCHAR NOT NULL,
  user_id BIGINT NOT NULL,
  CONSTRAINT fk_login_info_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
  CONSTRAINT unique_login_info UNIQUE (provider_id, provider_key)
);

-- CREATE INDEX FOR FASTER LOGIN LOOKUPS
CREATE INDEX idx_login_info_provider_key ON login_info (provider_id, provider_key);
CREATE INDEX idx_login_info_user_id ON login_info (user_id);

-- !Downs
DROP TABLE oauth2_tokens;
DROP TABLE login_info;
DROP TABLE user_streamer_state;
DROP TABLE yt_streamer;
DROP TABLE yt_users;
DROP TABLE users;
DROP TABLE people;