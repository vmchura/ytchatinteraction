# --- !Ups
CREATE TABLE user_timezones (
    user_id bigint NOT NULL PRIMARY KEY,
    timezone varchar(50) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_timezones_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE TABLE user_availability (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL,
    from_week_day integer NOT NULL CHECK (from_week_day >= 1 AND from_week_day <= 7),
    to_week_day integer NOT NULL CHECK (to_week_day >= 1 AND to_week_day <= 7),
    from_hour_inclusive integer NOT NULL CHECK (from_hour_inclusive >= 0 AND from_hour_inclusive <= 23),
    to_hour_exclusive integer NOT NULL CHECK (to_hour_exclusive >= 1 AND to_hour_exclusive <= 24),
    availability_status varchar(20) NOT NULL CHECK (availability_status IN ('MAYBE_AVAILABLE', 'HIGHLY_AVAILABLE')),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_availability_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT chk_user_availability_time_range CHECK (to_hour_exclusive > from_hour_inclusive),
    CONSTRAINT chk_user_availability_week_day_range CHECK (to_week_day >= from_week_day)
);

CREATE INDEX idx_user_availability_user_id ON user_availability (user_id);

CREATE INDEX idx_user_availability_status ON user_availability (availability_status);

CREATE INDEX idx_user_availability_time_range ON user_availability (from_week_day, to_week_day, from_hour_inclusive, to_hour_exclusive);

CREATE TABLE potential_matches (
    id bigserial PRIMARY KEY,
    first_user_id bigint NOT NULL,
    second_user_id bigint NOT NULL,
    match_start_time timestamp with time zone NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'Potential' CHECK (status IN ('Potential', 'Declined', 'Accepted')),
    first_user_availability_id bigint NOT NULL,
    second_user_availability_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_potential_matches_first_user FOREIGN KEY (first_user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_potential_matches_second_user FOREIGN KEY (second_user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_potential_matches_first_user_availability FOREIGN KEY (first_user_availability_id) REFERENCES user_availability (id) ON DELETE SET NULL,
    CONSTRAINT fk_potential_matches_second_user_availability FOREIGN KEY (second_user_availability_id) REFERENCES user_availability (id) ON DELETE SET NULL,
    CONSTRAINT chk_potential_matches_different_users CHECK (first_user_id != second_user_id),
    CONSTRAINT chk_potential_matches_at_least_one_availability CHECK (first_user_availability_id IS NOT NULL OR second_user_availability_id IS NOT NULL)
);

CREATE INDEX idx_potential_matches_first_user ON potential_matches (first_user_id);

CREATE INDEX idx_potential_matches_second_user ON potential_matches (second_user_id);

CREATE INDEX idx_potential_matches_status ON potential_matches (status);

CREATE INDEX idx_potential_matches_match_time ON potential_matches (match_start_time);

# --- !Downs
DROP TABLE IF EXISTS potential_matches;

DROP TABLE IF EXISTS user_availability;

DROP TABLE IF EXISTS user_timezones;


