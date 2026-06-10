-- 파싱 실행 재시도 상한 카운터 (execution at-least-once, #461).
-- 디스패처가 PENDING 을 claim 할 때(markProcessing) 1 이 되고, recover 가 stale PROCESSING 을 재실행할 때마다 +1 된다.
-- recover 는 attempt_count 가 상한(현재 2)에 도달하면 재실행 대신 FAILED 로 종결한다 — 무한 재큐잉 방지.
-- attempt_count 증가는 곧 updated_at 을 갱신(dirty checking)해, 재실행 시점부터 stale 시계가 다시 흐르게 한다.
--
-- additive·commutative: 기존 행은 DEFAULT 0 으로 채워지고, 적용 순서가 결과를 바꾸지 않는다.
-- 조회 필터가 아니라 fetch 후 메모리 분기 값이라 별도 인덱스는 두지 않는다 (recover 는 (status, updated_at) 인덱스로 조회).
ALTER TABLE item_snapshots
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
