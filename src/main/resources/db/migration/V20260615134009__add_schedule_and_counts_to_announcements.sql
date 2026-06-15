-- 공지 예약 발송 + 진행률/집계(#489). 모두 additive(컬럼 추가)라 out-of-order 안전.
-- scheduled_at: 예약 발송 시각(NULL = 즉시 발송 경로). status 는 DRAFT→SCHEDULED→SENDING→SENT 로 확장(컬럼 자체는 기존 VARCHAR(20) 유지).
-- total/success/failure_count: 발송 진행률(attempted=success+failure)과 결과 집계 표시용. 기존 recipient_count 는 "발송 시점 대상자 수"로 유지.
-- skipped_count: 미도달(토큰 없음 · FCM 미설정) 수. 진행률 분자는 success+failure+skipped(처리 완료 수)라, 토큰 없는 유저가 있어도 100%에 도달한다.
ALTER TABLE announcements
    ADD COLUMN scheduled_at  DATETIME(6) NULL,
    ADD COLUMN total_count   INT NOT NULL DEFAULT 0,
    ADD COLUMN success_count INT NOT NULL DEFAULT 0,
    ADD COLUMN failure_count INT NOT NULL DEFAULT 0,
    ADD COLUMN skipped_count INT NOT NULL DEFAULT 0;

-- 예약 폴링 스케줄러가 scheduled_at <= now 인 SCHEDULED 건만 빠르게 집어오게 하는 인덱스.
CREATE INDEX idx_announcements_status_scheduled ON announcements (status, scheduled_at);
