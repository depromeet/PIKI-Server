-- 상품 버저닝 4b(정규화): wish·tournament_item 의 item_id 를 제거하고 snapshot_id 를 단일 출처(NOT NULL)로 승격한다.
-- item 정체성은 item_snapshots.item_id 한 곳에만 둔다 — wish/tournament_item 은 snapshot_id 로 도달한다.
-- FK 제약 없음(프로젝트 정책). 순서 의존(정리→NOT NULL→DROP)이라 한 파일에서 순차 적용한다.

-- snapshot_id 미충족(3단계 전환 이전) 행 정리 — backfill 없이 드롭(dev 단일·보존 데이터 없음, 2·4a 선례).
DELETE FROM wishes WHERE snapshot_id IS NULL;
DELETE FROM tournament_items WHERE snapshot_id IS NULL;

-- snapshot_id NOT NULL 승격 — 이제 모든 행이 활성/고정 snapshot 을 가리킨다.
ALTER TABLE wishes MODIFY COLUMN snapshot_id BIGINT NOT NULL;
ALTER TABLE tournament_items MODIFY COLUMN snapshot_id BIGINT NOT NULL;

-- item_id 컬럼 제거 (+ 의존 인덱스). 조회는 snapshot 조인으로 itemId 에 도달한다.
ALTER TABLE wishes DROP INDEX idx_wishes_item_id, DROP COLUMN item_id;

-- tournament_items: 중복 출전 방지 유니크를 item_id 기준에서 snapshot_id 기준으로 재정의한다.
-- 한 토너먼트 생성은 한 시점이라 item→활성 snapshot 이 1:1 이고, 링크·이미지 출전은 매번 새 snapshot 이라
-- 같은 item 이 다른 snapshot 으로 중복 출전하는 경로가 없어 "같은 상품 두 번 금지" 의미가 보존된다.
ALTER TABLE tournament_items DROP INDEX uk_tournament_items, DROP COLUMN item_id;
ALTER TABLE tournament_items ADD UNIQUE KEY uk_tournament_items (tournament_id, snapshot_id);
