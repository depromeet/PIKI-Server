-- admin 조회 도구(list_recent_items)의 'where deleted_at is null order by created_at desc LIMIT n' 이
-- full table scan + filesort 를 타지 않도록 복합 인덱스를 추가한다. deleted_at(등치) + created_at(정렬)
-- 순서라 필터와 정렬을 한 인덱스로 처리해 LIMIT 가 정렬을 short-circuit 한다. FK 아님(조회 인덱스).
ALTER TABLE items ADD KEY idx_items_deleted_created (deleted_at, created_at);
