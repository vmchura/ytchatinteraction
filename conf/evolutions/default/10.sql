# Create user_smurfs table to track in-game aliases used by users in matches

# --- !Ups

CREATE TABLE user_smurfs (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    tournament_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    smurf VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Foreign key constraints
ALTER TABLE user_smurfs ADD CONSTRAINT user_smurfs_match_fk 
    FOREIGN KEY (match_id) REFERENCES tournament_matches(match_id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE user_smurfs ADD CONSTRAINT user_smurfs_tournament_fk 
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE user_smurfs ADD CONSTRAINT user_smurfs_user_fk 
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON UPDATE RESTRICT ON DELETE CASCADE;

-- Indexes for performance
CREATE INDEX idx_user_smurfs_match_id ON user_smurfs(match_id);
CREATE INDEX idx_user_smurfs_tournament_id ON user_smurfs(tournament_id);
CREATE INDEX idx_user_smurfs_user_id ON user_smurfs(user_id);
CREATE INDEX idx_user_smurfs_created_at ON user_smurfs(created_at);

-- Composite indexes for common query patterns
CREATE INDEX idx_user_smurfs_match_user ON user_smurfs(match_id, user_id);
CREATE INDEX idx_user_smurfs_tournament_user ON user_smurfs(tournament_id, user_id);
CREATE INDEX idx_user_smurfs_tournament_smurf ON user_smurfs(tournament_id, smurf);

-- Unique constraint to prevent duplicate smurf records for the same user in the same match
CREATE UNIQUE INDEX idx_user_smurfs_unique_match_user ON user_smurfs(match_id, user_id);

# --- !Downs

DROP TABLE IF EXISTS user_smurfs;
