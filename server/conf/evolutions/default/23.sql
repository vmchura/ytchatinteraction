# --- !Ups
CREATE TABLE prediction_events (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR NOT NULL,
  status VARCHAR NOT NULL DEFAULT 'Open',
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE prediction_matchups (
  id BIGSERIAL PRIMARY KEY,
  event_id BIGINT NOT NULL REFERENCES prediction_events(id),
  round INT NOT NULL,
  match_label VARCHAR NOT NULL,
  bet_type VARCHAR NOT NULL,
  player1_name VARCHAR,
  player1_race VARCHAR,
  player2_name VARCHAR,
  player2_race VARCHAR,
  player3_name VARCHAR,
  player3_race VARCHAR,
  player4_name VARCHAR,
  player4_race VARCHAR,
  multipliers JSONB NOT NULL,
  result JSONB,
  status VARCHAR NOT NULL DEFAULT 'Open',
  result_scored_at TIMESTAMP
);

CREATE TABLE prediction_bets (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(user_id),
  matchup_id BIGINT NOT NULL REFERENCES prediction_matchups(id),
  selection VARCHAR NOT NULL,
  points_bet INT NOT NULL CHECK (points_bet > 0),
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE(user_id, matchup_id, selection)
);

CREATE TABLE prediction_user_scores (
  user_id BIGINT NOT NULL REFERENCES users(user_id),
  event_id BIGINT NOT NULL REFERENCES prediction_events(id),
  total_score INT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, event_id)
);

# --- !Downs
DROP TABLE IF EXISTS prediction_user_scores;
DROP TABLE IF EXISTS prediction_bets;
DROP TABLE IF EXISTS prediction_matchups;
DROP TABLE IF EXISTS prediction_events;
