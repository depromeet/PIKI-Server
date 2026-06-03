-- item 의 추출 버전(스냅샷). 한 item(정체성)이 갱신될 때마다 새 버전 행이 쌓인다.
-- Epic #362(상품 버저닝)의 1단계: item 을 정체성(items)과 버전(item_snapshots)으로 분리하기 위한
-- 첫 additive 단계. 새 테이블을 추가만 하고 기존 items 와 병존하며, 아직 어디서도 참조하지 않는다
-- (쓰기 이중화·백필·참조 이전은 후속 단계). FK 제약은 두지 않는다(프로젝트 정책) — 참조 무결성은 애플리케이션이 책임.
CREATE TABLE item_snapshots (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    item_id       BIGINT        NOT NULL,
    version       INT           NOT NULL,
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
    -- (item_id, version) 은 한 item 의 버전 자연키 — UNIQUE 로 같은 version 중복·동시 갱신 race 의 재삽입을 DB 가 차단한다.
    -- 겸하여 버전 조회·가격 히스토리 인덱스이며, (item_id) 단독 조회는 leftmost prefix 로 커버. (UNIQUE 는 FK 가 아니므로 프로젝트 FK 금지 정책과 무관)
    UNIQUE KEY uq_item_snapshots_item_id_version (item_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
