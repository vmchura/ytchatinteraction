# Create analytical_files table

# --- !Ups

CREATE TABLE analytical_files (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL UNIQUE,
    original_name VARCHAR(500) NOT NULL,
    relative_directory_path VARCHAR(1000) NOT NULL,
    saved_file_name VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    slot_player_id INT NOT NULL,
    user_race VARCHAR(12) NOT NULL,
    rival_race VARCHAR(12) NOT NULL,
    game_frames INT NOT NULL
);

-- Foreign key constraints
ALTER TABLE analytical_files ADD CONSTRAINT analytical_files_user_fk
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON UPDATE RESTRICT ON DELETE CASCADE;

# --- !Downs

DROP TABLE IF EXISTS analytical_files;