# --- !Ups

-- Add winner_option_id column to event_polls table
ALTER TABLE event_polls 
ADD COLUMN winner_option_id INTEGER NULL;

-- Add foreign key constraint for winner_option_id referencing poll_options
ALTER TABLE event_polls
ADD CONSTRAINT fk_event_polls_winner_option
FOREIGN KEY (winner_option_id)
REFERENCES poll_options (option_id);

# --- !Downs

-- Remove foreign key constraint
ALTER TABLE event_polls
DROP CONSTRAINT IF EXISTS fk_event_polls_winner_option;

-- Remove the winner_option_id column
ALTER TABLE event_polls
DROP COLUMN IF EXISTS winner_option_id;
