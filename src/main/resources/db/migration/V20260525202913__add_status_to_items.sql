-- items 에 파싱 상태(status) 컬럼 추가.
-- PROCESSING(등록 직후 파싱 중) / READY(파싱 완료) / FAILED(파싱 실패) 를 ItemStatus enum 의 name 으로 저장한다.
-- 기존 행은 모두 추출이 끝난 완성 상품이므로 READY 로 backfill 한다 (DEFAULT 'READY').
ALTER TABLE items
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'READY';
