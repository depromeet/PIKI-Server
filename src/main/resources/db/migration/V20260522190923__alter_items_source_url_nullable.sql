-- OCR 등록 경로는 URL 이 없어 link(source_url)가 비어 있는 item 이 생긴다.
-- URL 추출 경로는 여전히 값을 채우지만, 컬럼 자체는 nullable 로 풀어 두 경로가 같은 테이블을 공유한다.
ALTER TABLE items MODIFY COLUMN source_url VARCHAR(2048) NULL;
