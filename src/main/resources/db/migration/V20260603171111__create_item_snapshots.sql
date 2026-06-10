-- item 의 추출 버전(스냅샷). 한 item(정체성)이 갱신될 때마다 새 행이 쌓인다.
-- Epic #362(상품 버저닝)의 1단계: item 을 정체성(items)과 버전(item_snapshots)으로 분리하기 위한
-- 첫 additive 단계. 새 테이블을 추가만 하고 기존 items 와 병존하며, 아직 어디서도 참조하지 않는다
-- (쓰기 이중화·백필·참조 이전은 후속 단계). FK 제약은 두지 않는다(프로젝트 정책) — 참조 무결성은 애플리케이션이 책임.
-- 버전 순서는 id(auto increment, 단조증가)로 충분하므로 별도 version 컬럼을 두지 않는다 — 추가돼도 기존 행 순서가 변하지 않는다.
CREATE TABLE item_snapshots (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    item_id       BIGINT        NOT NULL,
    name          VARCHAR(512)  NULL,
    image_url     VARCHAR(2048) NULL,
    current_price INT           NULL,
    currency      VARCHAR(8)    NULL,
    status        VARCHAR(16)   NOT NULL,
    extracted_at  DATETIME(6)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    deleted_at    DATETIME(6)   NULL,
    PRIMARY KEY (id),
    -- 한 item 의 버전(스냅샷)들을 조회·시간순 정렬(id)하기 위한 인덱스.
    KEY idx_item_snapshots_item_id (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
