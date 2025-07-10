-- !Ups

-- Create table for tournaments
CREATE TABLE tournaments (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  max_participants INTEGER NOT NULL,
  registration_start_at TIMESTAMP NOT NULL,
  registration_end_at TIMESTAMP NOT NULL,
  tournament_start_at TIMESTAMP,
  tournament_end_at TIMESTAMP,
  challonge_tournament_id BIGINT,
  status VARCHAR(20) NOT NULL DEFAULT 'RegistrationOpen',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_tournaments_status 
    CHECK (status IN ('RegistrationOpen', 'RegistrationClosed', 'InProgress', 'Completed', 'Cancelled')),
  CONSTRAINT chk_tournaments_max_participants
    CHECK (max_participants > 0),
  CONSTRAINT chk_tournaments_registration_dates
    CHECK (registration_end_at > registration_start_at),
  CONSTRAINT chk_tournaments_tournament_dates
    CHECK (tournament_end_at IS NULL OR tournament_start_at IS NULL OR tournament_end_at >= tournament_start_at)
);


-- Create table for tournament registrations
CREATE TABLE tournament_registrations (
  id BIGSERIAL PRIMARY KEY,
  tournament_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(30) NOT NULL DEFAULT 'Registered',
  CONSTRAINT fk_tournament_registrations_tournament
    FOREIGN KEY (tournament_id)
    REFERENCES tournaments (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_tournament_registrations_user
    FOREIGN KEY (user_id)
    REFERENCES users (user_id)
    ON DELETE CASCADE,
  CONSTRAINT chk_tournament_registrations_status
    CHECK (status IN ('Registered', 'Confirmed', 'Withdrawn', 'DisqualifiedByAdmin'))
);


-- Create table for tournament matches
CREATE TABLE tournament_matches (
  match_id BIGINT PRIMARY KEY,
  tournament_id BIGINT NOT NULL,
  first_user_id BIGINT NOT NULL,
  second_user_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(20) NOT NULL DEFAULT 'Pending',
  CONSTRAINT chk_tournament_matches_status 
    CHECK (status IN ('Pending', 'InProgress', 'Completed', 'Disputed', 'Cancelled'))
);


-- Add foreign key constraint to tournament_matches table
ALTER TABLE tournament_matches 
ADD CONSTRAINT fk_tournament_matches_tournament
FOREIGN KEY (tournament_id)
REFERENCES tournaments (id)
ON DELETE CASCADE;

-- !Downs

-- Remove foreign key constraint from tournament_matches
ALTER TABLE tournament_matches 
DROP CONSTRAINT IF EXISTS fk_tournament_matches_tournament;

-- Drop tournament_matches table
DROP TABLE IF EXISTS tournament_matches;

-- Drop tournament_registrations table
DROP TABLE IF EXISTS tournament_registrations;

-- Drop tournaments table
DROP TABLE IF EXISTS tournaments;
