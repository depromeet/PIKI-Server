-- 상품 버저닝 3단계(참조 이전): wish/tournament_item 이 snapshot 을 참조하도록 snapshot_id 추가 (additive).
-- nullable — 신규 등록·추출부터 채워진다(쓰기 연결). 기존 dev 데이터는 비우고 가므로 backfill 하지 않는다.
-- FK 제약 없음(프로젝트 정책). item_id 컬럼은 조회 전환이 안정된 뒤 4단계에서 제거한다.
ALTER TABLE wishes ADD COLUMN snapshot_id BIGINT NULL;
ALTER TABLE tournament_items ADD COLUMN snapshot_id BIGINT NULL;

-- 조회 시 snapshot 을 끌어오는 wish/tournament_item → snapshot 방향 조인용 인덱스.
CREATE INDEX idx_wishes_snapshot_id ON wishes (snapshot_id);
CREATE INDEX idx_tournament_items_snapshot_id ON tournament_items (snapshot_id);
