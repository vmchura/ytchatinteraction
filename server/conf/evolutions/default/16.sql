# --- !Ups
ALTER TABLE analytical_files
    ADD COLUMN other_user_id bigint;

ALTER TABLE analytical_files
    ADD COLUMN algorithm_id varchar(64);

ALTER TABLE analytical_files
    ADD CONSTRAINT analytical_files_other_user_fk FOREIGN KEY (other_user_id) REFERENCES users (user_id) ON UPDATE RESTRICT ON DELETE CASCADE;

# --- !Downs
ALTER TABLE analytical_files
    DROP CONSTRAINT analytical_files_other_user_fk;

ALTER TABLE analytical_files
    DROP COLUMN other_user_id;

ALTER TABLE analytical_files
    DROP COLUMN algorithm_id;


