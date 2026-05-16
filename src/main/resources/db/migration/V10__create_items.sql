CREATE TABLE items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_url VARCHAR(2048) NOT NULL,
    name VARCHAR(512) NULL,
    image_url VARCHAR(2048) NULL,
    current_price INT NULL,
    currency VARCHAR(8) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
