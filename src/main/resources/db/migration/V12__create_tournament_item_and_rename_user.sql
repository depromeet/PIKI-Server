CREATE TABLE tournament_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tournament_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    user_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_tournament_item_tournament_id (tournament_id),
    UNIQUE KEY uk_tournament_item (tournament_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V8 에서 tournament_participant 로 만든 참여자 테이블을 tournament_user 로 통일한다.
RENAME TABLE tournament_participant TO tournament_user;
ALTER TABLE tournament_user RENAME INDEX uk_tp_tournament_user TO uk_tournament_user;
