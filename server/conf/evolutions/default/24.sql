# --- !Ups
-- New simplified prediction model: each user stores ONE prediction per matchup
-- (an ordered ranking of player keys for groups, or a single chosen score for matches).
-- No budget, no multipliers. Old prediction data is intentionally discarded.

DROP TABLE IF EXISTS prediction_bets;

CREATE TABLE prediction_bets (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id),
  matchup_id BIGINT NOT NULL REFERENCES prediction_matchups(id),
  ranking TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE(user_id, matchup_id)
);

ALTER TABLE prediction_matchups DROP COLUMN IF EXISTS multipliers;

# --- !Downs
ALTER TABLE prediction_matchups ADD COLUMN multipliers TEXT NOT NULL DEFAULT '{}';

DROP TABLE IF EXISTS prediction_bets;

CREATE TABLE prediction_bets (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id),
  matchup_id BIGINT NOT NULL REFERENCES prediction_matchups(id),
  selection VARCHAR NOT NULL,
  points_bet INT NOT NULL CHECK (points_bet > 0),
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE(user_id, matchup_id, selection)
);
