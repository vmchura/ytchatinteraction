# --- !Ups
CREATE TABLE elo_users (
    user_id bigint NOT NULL,
    user_race varchar(12) NOT NULL,
    rival_race varchar(12) NOT NULL,
    updated_at timestamp with time zone NOT NULL
);

ALTER TABLE elo_users
    ADD CONSTRAINT elo_users_primary_key PRIMARY KEY (user_id, user_race, rival_race);

ALTER TABLE elo_users
    ADD CONSTRAINT elo_users_fk FOREIGN KEY (user_id) REFERENCES users (user_id) ON UPDATE RESTRICT ON DELETE CASCADE;

CREATE TABLE elo_users_log (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL,
    user_race varchar(12) NOT NULL,
    rival_user_id bigint NOT NULL,
    rival_race varchar(12) NOT NULL,
    event_at timestamp with time zone NOT NULL,
    user_initial_elo int NOT NULL,
    rival_initial_elo int NOT NULL,
    match_id bigint,
    casual_match_id bigint,
    user_new_elo int NOT NULL
);

ALTER TABLE elo_users_log
    ADD CONSTRAINT elo_users_log_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE elo_users_log
    ADD CONSTRAINT elo_users_log_rival_users FOREIGN KEY (rival_user_id) REFERENCES users (user_id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE elo_users_log
    ADD CONSTRAINT elo_users_log_matchs FOREIGN KEY (match_id) REFERENCES tournament_matches (match_id) ON UPDATE RESTRICT ON DELETE CASCADE;

# --- !Downs
ALTER TABLE elo_users
    DROP CONSTRAINT IF EXISTS elo_users_fk;

DROP TABLE IF EXISTS elo_users;

ALTER TABLE elo_users_log
    DROP CONSTRAINT elo_users_log_users;

ALTER TABLE elo_users_log
    DROP CONSTRAINT elo_users_log_rival_users;

ALTER TABLE elo_users_log
    DROP CONSTRAINT elo_users_log_matchs;

DROP TABLE IF EXISTS elo_users_log;


