CREATE TABLE tournament_participant (
    tournament_id BIGINT      NOT NULL,
    user_id       BINARY(16)  NOT NULL COMMENT 'UUID V4',
    role          VARCHAR(10) NOT NULL COMMENT 'OWNER / FRIEND',
    joined_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (tournament_id, user_id),
    CONSTRAINT fk_tp_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id),
    CONSTRAINT fk_tp_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
