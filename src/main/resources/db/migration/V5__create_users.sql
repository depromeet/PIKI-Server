CREATE TABLE user (
    id            BINARY(16)  NOT NULL COMMENT 'UUID V4',
    nickname      VARCHAR(16) NOT NULL,
    identity_type VARCHAR(10) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    deleted_at    DATETIME(6) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
