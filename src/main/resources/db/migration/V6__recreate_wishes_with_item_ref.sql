-- wish 에 박혀 있던 상품 정보를 items 로 분리한다.
-- dev 단계라 기존 데이터는 보존하지 않고 테이블을 재생성한다.
DROP TABLE wishes;

CREATE TABLE wishes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BINARY(16) NOT NULL,
    item_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_wishes_user_id (user_id),
    KEY idx_wishes_item_id (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
