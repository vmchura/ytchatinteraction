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
    availability_status varchar(20) NOT NULL CHECK (availability_status IN ('UNAVAILABLE', 'MAYBE_AVAILABLE', 'HIGHLY_AVAILABLE')),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_availability_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT chk_user_availability_time_range CHECK (to_hour_exclusive > from_hour_inclusive),
    CONSTRAINT chk_user_availability_week_day_range CHECK (to_week_day >= from_week_day)
);

CREATE INDEX idx_user_availability_user_id ON user_availability (user_id);

CREATE INDEX idx_user_availability_status ON user_availability (availability_status);

CREATE INDEX idx_user_availability_time_range ON user_availability (from_week_day, to_week_day, from_hour_inclusive, to_hour_exclusive);

# --- !Downs
DROP TABLE IF EXISTS user_availability;

DROP TABLE IF EXISTS user_timezones;


