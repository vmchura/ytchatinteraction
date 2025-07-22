-- !Ups
-- Create table to track user alias history
CREATE TABLE user_alias_history (
  id SERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  alias VARCHAR(100) NOT NULL UNIQUE,
  is_current BOOLEAN DEFAULT FALSE,
  assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  replaced_at TIMESTAMP,
  generation_method VARCHAR(50) NOT NULL, -- 'random_registration', 'user_change', 'admin_change'
  CONSTRAINT fk_user_alias_history_user 
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

-- !Downs

DROP TABLE IF EXISTS user_alias_history;
