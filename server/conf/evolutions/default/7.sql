-- !Ups

-- Create table for tournament Challonge participant mappings
CREATE TABLE tournament_challonge_participants (
  id BIGSERIAL PRIMARY KEY,
  tournament_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  challonge_participant_id BIGINT NOT NULL,
  challonge_tournament_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  
  -- Foreign key constraints
  CONSTRAINT fk_tournament_challonge_participants_tournament
    FOREIGN KEY (tournament_id)
    REFERENCES tournaments (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_tournament_challonge_participants_user
    FOREIGN KEY (user_id)
    REFERENCES users (user_id)
    ON DELETE CASCADE,
    
  -- Unique constraint to prevent duplicate mappings for the same tournament/user combination
  CONSTRAINT uq_tournament_challonge_participants_tournament_user
    UNIQUE (tournament_id, user_id)
);

-- Create indexes for better performance
CREATE INDEX idx_tournament_challonge_participants_challonge_id 
  ON tournament_challonge_participants (challonge_participant_id);

CREATE INDEX idx_tournament_challonge_participants_challonge_tournament_id 
  ON tournament_challonge_participants (challonge_tournament_id);

CREATE INDEX idx_tournament_challonge_participants_tournament_id 
  ON tournament_challonge_participants (tournament_id);

CREATE INDEX idx_tournament_challonge_participants_user_id 
  ON tournament_challonge_participants (user_id);

-- !Downs

-- Drop indexes
DROP INDEX IF EXISTS idx_tournament_challonge_participants_user_id;
DROP INDEX IF EXISTS idx_tournament_challonge_participants_tournament_id;
DROP INDEX IF EXISTS idx_tournament_challonge_participants_challonge_tournament_id;
DROP INDEX IF EXISTS idx_tournament_challonge_participants_challonge_id;

-- Drop table
DROP TABLE IF EXISTS tournament_challonge_participants;
