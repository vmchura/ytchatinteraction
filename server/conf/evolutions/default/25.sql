# --- !Ups
-- Custom display names per round (e.g. "Round of 16", "Semifinals"), stored as a
-- JSON map {"1": "Round of 16", ...}. Empty map falls back to "Round N".
ALTER TABLE prediction_events ADD COLUMN round_names TEXT NOT NULL DEFAULT '{}';

# --- !Downs
ALTER TABLE prediction_events DROP COLUMN round_names;
