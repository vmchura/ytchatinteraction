# --- !Ups
CREATE TABLE casual_match (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL,
    rival_user_id bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    status varchar(12) NOT NULL
);

CREATE TABLE casual_match_files (
    id bigserial PRIMARY KEY,
    casual_match_id bigint NOT NULL,
    sha256_hash varchar(64) NOT NULL UNIQUE,
    original_name varchar(500) NOT NULL,
    relative_directory_path varchar(1000) NOT NULL,
    saved_file_name varchar(500) NOT NULL,
    uploaded_at timestamp with time zone NOT NULL,
    slot_player_id int NOT NULL,
    rival_slot_player_id int NOT NULL,
    user_race varchar(12) NOT NULL,
    rival_race varchar(12) NOT NULL,
    game_frames int NOT NULL
);

ALTER TABLE casual_match
    ADD CONSTRAINT casual_match_user_fk FOREIGN KEY (user_id) REFERENCES users (user_id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE casual_match
    ADD CONSTRAINT casual_match_rival_user_fk FOREIGN KEY (rival_user_id) REFERENCES users (user_id) ON UPDATE RESTRICT ON DELETE CASCADE;


ALTER TABLE casual_match_files
    ADD CONSTRAINT casual_match_files_match_fk FOREIGN KEY (casual_match_id) REFERENCES casual_match (id) ON UPDATE RESTRICT ON DELETE CASCADE;

# --- !Downs
ALTER TABLE casual_match_files DROP CONSTRAINT casual_match_files_match_fk;
ALTER TABLE casual_match DROP CONSTRAINT casual_match_user_fk;
ALTER TABLE casual_match DROP CONSTRAINT casual_match_rival_user_fk;
DROP TABLE IF EXISTS casual_match_files;
DROP TABLE IF EXISTS casual_match;


