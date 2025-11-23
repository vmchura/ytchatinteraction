# Create analytical_result table

# --- !Ups

CREATE TABLE analytical_result (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    match_id BIGINT NOT NULL,
    userRace VARCHAR(12) NOT NULL,
    rivalRace VARCHAR(12) NOT NULL,
    originalFileName VARCHAR(500) NOT NULL,
    analysis_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    analysis_finished_at TIMESTAMP WITH TIME ZONE,
    algorith_version VARCHAR(12),
    result BOOLEAN
);

-- Foreign key constraints
ALTER TABLE analytical_result ADD CONSTRAINT analytical_result_user_fk
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE analytical_result ADD CONSTRAINT analytical_result_match_fk
    FOREIGN KEY (match_id) REFERENCES tournament_matches(match_id) ON UPDATE RESTRICT ON DELETE CASCADE;

# --- !Downs

DROP TABLE IF EXISTS analytical_result;