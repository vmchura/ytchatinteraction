# --- !Ups

DROP INDEX idx_user_smurfs_unique_match_user;
ALTER TABLE tournament_matches
ADD COLUMN winner_description VARCHAR(30) NOT NULL DEFAULT 'Undefined';
# --- !Downs

CREATE UNIQUE INDEX idx_user_smurfs_unique_match_user ON user_smurfs(match_id, user_id);

ALTER TABLE tournament_matches
DROP COLUMN winner_description;