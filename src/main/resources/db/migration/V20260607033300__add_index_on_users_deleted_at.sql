-- 회원 탈퇴 30일 파기 스케줄러(DailyAccountPurgeScheduler)가 deleted_at 범위로 tombstone 을 스캔한다.
-- 인덱스가 없으면 full scan 이 되므로 deleted_at 에 인덱스를 추가한다. additive(순서 무관), FK 없음.
CREATE INDEX idx_users_deleted_at ON users (deleted_at);
