-- 알림 히스토리 커서 페이지네이션용 (user_id, id) 인덱스 (#246).
-- 목록 쿼리는 `WHERE user_id = ? [AND id < ?] ORDER BY id DESC LIMIT n` 형태다.
-- 기존 idx_notifications_user_id_is_read (user_id, is_read) 는 유저 내부 정렬이 (is_read, id) 라
-- is_read 로 안 거르는 목록 쿼리의 id DESC 정렬을 못 받쳐 매 조회 filesort 가 붙는다 (LIMIT 조기종료도 못 씀).
-- (user_id, id) 를 두면 id DESC + id < cursor 가 역방향 인덱스 레인지 스캔으로 풀려 딱 n건만 읽는다.
-- 기존 (user_id, is_read) 는 안읽음 수 집계(countByUserIdAndIsReadFalse)와 is_read=false 조건에 여전히 유효해 그대로 둔다.
-- additive 라 out-of-order 무해. FK 없음(프로젝트 정책).
CREATE INDEX idx_notifications_user_id_id ON notifications (user_id, id);
