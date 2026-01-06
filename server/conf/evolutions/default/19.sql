-- !Ups
ALTER TABLE tournaments ADD COLUMN tournament_code VARCHAR(6) NOT NULL DEFAULT 'legacy';
-- !Downs
ALTER TABLE tournaments DROP COLUMN tournament_code;
