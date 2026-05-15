DROP TABLE guests;

ALTER TABLE tournament
    ADD CONSTRAINT fk_tournament_user FOREIGN KEY (user_id) REFERENCES user (id);
