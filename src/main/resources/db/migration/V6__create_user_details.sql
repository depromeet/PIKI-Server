CREATE TABLE user_detail (
    user_id       BINARY(16)   NOT NULL COMMENT 'UUID V4',
    profile_image VARCHAR(255) NOT NULL DEFAULT 'default',
    email         VARCHAR(255) NOT NULL,
    provider      VARCHAR(20)  NOT NULL CHECK (provider IN ('KAKAO', 'GOOGLE', 'APPLE')),
    social_id     VARCHAR(255) NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_detail_provider_social (provider, social_id),
    CONSTRAINT fk_user_detail_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
