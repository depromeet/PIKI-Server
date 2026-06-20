-- admin 백오피스의 최근 item 조회(ItemRepository.findRecent) 전용으로 추가했던 복합 인덱스를 제거한다.
-- 백오피스 제거로 items 테이블의 'deleted_at(등치) + created_at(정렬)' 조회 경로가 사라져 미사용이 됐다.
-- (item 도메인의 다른 createdAt 정렬 쿼리는 전부 item_snapshots 테이블이라 이 인덱스를 타지 않는다.)
-- 생성 마이그레이션(V20260529220201)은 적용 이력 보존을 위해 그대로 두고(적용된 파일 삭제 금지 규약), forward-only 로 제거한다.
ALTER TABLE items DROP INDEX idx_items_deleted_created;
