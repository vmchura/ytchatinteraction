-- !Ups

ALTER TABLE oauth2_tokens DROP CONSTRAINT IF EXISTS fk_oauth2_tokens_yt_users;

-- !Downs

ALTER TABLE oauth2_tokens ADD CONSTRAINT fk_oauth2_tokens_yt_users FOREIGN KEY (user_channel_id) REFERENCES yt_users (user_channel_id) ON DELETE CASCADE;
