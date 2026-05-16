ALTER TABLE wishes
    DROP INDEX uk_wishes_guest_source,
    DROP INDEX idx_wishes_guest_id,
    RENAME COLUMN guest_id TO user_id,
    ADD UNIQUE KEY uk_wishes_user_source (user_id, source_url(512)),
    ADD KEY idx_wishes_user_id (user_id),
    ADD CONSTRAINT fk_wishes_user FOREIGN KEY (user_id) REFERENCES user (id);
