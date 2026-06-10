-- 직전 마이그레이션이 옛 스키마를 모두 drop 한 상태에서 시작.
-- 정책: FK 없음 / 테이블명 복수형 통일 / status DEFAULT 는 엔티티 기본값(PENDING) 과 일치

CREATE TABLE users (
    id            BINARY(16)    NOT NULL COMMENT 'UUID V4',
    nickname      VARCHAR(10)   NOT NULL,
    profile_image VARCHAR(2048) NOT NULL DEFAULT 'https://api.dicebear.com/9.x/bottts/svg?seed=default',
    identity_type VARCHAR(10)   NOT NULL CHECK (identity_type IN ('GUEST', 'MEMBER')),
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    deleted_at    DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_details (
    user_id    BINARY(16)   NOT NULL COMMENT 'UUID V4',
    email      VARCHAR(255) NOT NULL,
    provider   VARCHAR(20)  NOT NULL CHECK (provider IN ('KAKAO', 'GOOGLE', 'APPLE')),
    social_id  VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    deleted_at DATETIME(6)  NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_details_provider_social (provider, social_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE items (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    source_url    VARCHAR(2048) NOT NULL,
    name          VARCHAR(512)  NULL,
    image_url     VARCHAR(2048) NULL,
    current_price INT           NULL,
    currency      VARCHAR(8)    NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    deleted_at    DATETIME(6)   NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wishes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BINARY(16)  NOT NULL,
    item_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_wishes_user_id (user_id),
    KEY idx_wishes_item_id (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tournaments (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    owner_tournament_user_id BIGINT       NOT NULL,
    name                     VARCHAR(255) NOT NULL,
    status                   VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    deleted_at               DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tournament_items (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tournament_id BIGINT      NOT NULL,
    item_id       BIGINT      NOT NULL,
    user_id       BINARY(16)  NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    deleted_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_tournament_items_tournament_id (tournament_id),
    UNIQUE KEY uk_tournament_items (tournament_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tournament_users (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tournament_id BIGINT      NOT NULL,
    user_id       BINARY(16)  NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    deleted_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tournament_users (tournament_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tournament_histories (
    id                          BIGINT      NOT NULL AUTO_INCREMENT,
    tournament_id               BIGINT      NOT NULL,
    current_round               INT         NOT NULL,
    first_tournament_item_id    BIGINT      NOT NULL,
    second_tournament_item_id   BIGINT      NOT NULL,
    selected_tournament_item_id BIGINT      NOT NULL,
    created_at                  DATETIME(6) NOT NULL,
    updated_at                  DATETIME(6) NOT NULL,
    deleted_at                  DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_tournament_histories_tournament_id (tournament_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
