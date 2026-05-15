CREATE TABLE tournament_participant (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tournament_id BIGINT      NOT NULL,
    user_id       BINARY(16)  NOT NULL COMMENT 'UUID V4',
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    deleted_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tp_tournament_user (tournament_id, user_id),
    CONSTRAINT fk_tp_tournament FOREIGN KEY (tournament_id) REFERENCES tournament (id) ON DELETE CASCADE,
    CONSTRAINT fk_tp_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
