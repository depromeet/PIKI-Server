-- soft delete 조회 패턴(user_id + deleted_at IS NULL + id DESC, cursor 는 id < ?)을 인덱스로 커버한다.
-- 기존 idx_wishes_user_id(user_id 단일)로는 deleted_at 필터와 id 정렬이 인덱스로 풀리지 않아
-- 데이터 증가 시 filesort 가 발생한다. 복합 인덱스로 정렬·cursor 페이지네이션까지 인덱스만으로 처리한다.
CREATE INDEX idx_wishes_user_deleted_id ON wishes (user_id, deleted_at, id);
