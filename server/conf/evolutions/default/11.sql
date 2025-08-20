# --- !Ups

DROP INDEX idx_user_smurfs_unique_match_user;

# --- !Downs

CREATE UNIQUE INDEX idx_user_smurfs_unique_match_user ON user_smurfs(match_id, user_id);