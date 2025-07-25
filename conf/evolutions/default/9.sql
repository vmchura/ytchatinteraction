# Create uploaded_files table

# --- !Ups

CREATE TABLE uploaded_files (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tournament_id BIGINT NOT NULL,
    match_id BIGINT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL UNIQUE,
    original_name VARCHAR(500) NOT NULL,
    relative_directory_path VARCHAR(1000) NOT NULL,
    saved_file_name VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Foreign key constraints
ALTER TABLE uploaded_files ADD CONSTRAINT uploaded_files_user_fk 
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE uploaded_files ADD CONSTRAINT uploaded_files_tournament_fk 
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE uploaded_files ADD CONSTRAINT uploaded_files_match_fk 
    FOREIGN KEY (match_id) REFERENCES tournament_matches(id) ON UPDATE RESTRICT ON DELETE CASCADE;

-- Indexes for performance
CREATE INDEX idx_uploaded_files_user_id ON uploaded_files(user_id);
CREATE INDEX idx_uploaded_files_tournament_id ON uploaded_files(tournament_id);
CREATE INDEX idx_uploaded_files_match_id ON uploaded_files(match_id);
CREATE UNIQUE INDEX idx_uploaded_files_sha256_hash ON uploaded_files(sha256_hash);
CREATE INDEX idx_uploaded_files_uploaded_at ON uploaded_files(uploaded_at);

-- Composite index for common query patterns
CREATE INDEX idx_uploaded_files_user_match ON uploaded_files(user_id, match_id);
CREATE INDEX idx_uploaded_files_tournament_match ON uploaded_files(tournament_id, match_id);

# --- !Downs

DROP TABLE IF EXISTS uploaded_files;