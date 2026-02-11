# --- !Ups
-- Add race column to tournament_registrations table
ALTER TABLE tournament_registrations 
ADD COLUMN race VARCHAR(10) NOT NULL DEFAULT 'Protoss';

-- Add check constraint to ensure valid race values
ALTER TABLE tournament_registrations 
ADD CONSTRAINT chk_tournament_registrations_race 
CHECK (race IN ('Protoss', 'Terran', 'Zerg'));

# --- !Downs
-- Remove check constraint
ALTER TABLE tournament_registrations 
DROP CONSTRAINT IF EXISTS chk_tournament_registrations_race;

-- Remove race column
ALTER TABLE tournament_registrations 
DROP COLUMN IF EXISTS race;
