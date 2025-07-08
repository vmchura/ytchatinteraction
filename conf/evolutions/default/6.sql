-- !Ups

-- Create table for tournament matches
CREATE TABLE tournament_matches (
  match_id VARCHAR(36) PRIMARY KEY,
  tournament_id VARCHAR(255) NOT NULL,
  first_user_id BIGINT NOT NULL,
  second_user_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(20) NOT NULL DEFAULT 'Pending',
  CONSTRAINT chk_tournament_matches_status 
    CHECK (status IN ('Pending', 'InProgress', 'Completed', 'Disputed', 'Cancelled'))
);

-- !Downs
DROP TABLE IF EXISTS tournament_matches;
