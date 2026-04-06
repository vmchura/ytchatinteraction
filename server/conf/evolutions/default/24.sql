# --- !Ups
ALTER TABLE prediction_matchups ALTER COLUMN multipliers TYPE TEXT USING multipliers::TEXT;
ALTER TABLE prediction_matchups ALTER COLUMN result TYPE TEXT USING result::TEXT;

# --- !Downs
ALTER TABLE prediction_matchups ALTER COLUMN multipliers TYPE JSONB USING multipliers::JSONB;
ALTER TABLE prediction_matchups ALTER COLUMN result TYPE JSONB USING result::JSONB;
