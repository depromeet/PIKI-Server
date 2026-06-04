-- 기존 items 각 행을 item_snapshots 의 첫 버전으로 백필한다. (Epic #362 상품 버저닝 2단계)
-- 쓰기 이중화 이후 등록된 item 은 이미 대응 snapshot 이 있으므로 NOT EXISTS 로 건너뛴다 — idempotent(재실행 안전).
--
-- 매핑:
--   extracted_at: 기존 items 엔 추출 시각 컬럼이 없어 알 수 없다. READY 면 updated_at(READY 전이 시점 근사)을, 그 외(PROCESSING·FAILED)는 NULL.
--   created_at/updated_at/deleted_at: item 값을 그대로 복사 — 그 버전이 item 과 같은 시점에 존재한 것으로 본다.
--
-- 경계 한계(인지): blue-green 배포에서 새 인스턴스가 이 백필을 돌린 뒤 전환 전까지, 옛 인스턴스(이중화 없는 코드)가
-- 만든 item 은 snapshot 이 누락된다. 또 이미 READY 인 누락 item 은 markReady self-heal 도 안 탄다.
-- 이 누락분은 3단계(참조 이전) 착수 시 같은 형태의 idempotent 백필을 한 번 더 적용해 메운다.
INSERT INTO item_snapshots
    (item_id, name, image_url, current_price, currency, status, extracted_at, created_at, updated_at, deleted_at)
SELECT
    i.id,
    i.name,
    i.image_url,
    i.current_price,
    i.currency,
    i.status,
    CASE WHEN i.status = 'READY' THEN i.updated_at ELSE NULL END,
    i.created_at,
    i.updated_at,
    i.deleted_at
FROM items i
WHERE NOT EXISTS (
    SELECT 1 FROM item_snapshots s WHERE s.item_id = i.id
);
