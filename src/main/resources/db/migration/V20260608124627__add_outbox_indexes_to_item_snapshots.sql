-- URL 파싱 경로의 outbox 전환용 폴링 인덱스.
-- 등록은 PENDING snapshot 을 커밋만 하고, 디스패처(짧은 주기)가 PENDING 을 집어 PROCESSING 으로 claim 한 뒤
-- 워커가 외부 LLM 추출을 수행한다. recover(긴 주기)는 워커가 죽어 PROCESSING 에 갇힌 행을 FAILED 로 정리한다.
--
-- 디스패처 조회: status='PENDING' 을 created_at 오름차순(FIFO)으로 limit → (status, created_at).
-- recover 조회: status='PROCESSING' 이고 updated_at 이 오래된(stale) 행 → (status, updated_at).
-- status 는 VARCHAR(16) 라 enum 값 추가(PENDING)는 스키마 변경이 필요 없다.
ALTER TABLE item_snapshots
    ADD INDEX idx_item_snapshots_status_created_at (status, created_at),
    ADD INDEX idx_item_snapshots_status_updated_at (status, updated_at);
