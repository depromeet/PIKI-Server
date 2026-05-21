-- 기획 반영: 닉네임 정책 16자 → 10자.
-- 현재 GUEST 닉네임 생성은 prefix + animal 조합으로 최대 9자라 기존 데이터 영향 없음.
-- VARCHAR(16) 컬럼을 VARCHAR(10) 로 좁힌다.
ALTER TABLE `user` MODIFY COLUMN nickname VARCHAR(10) NOT NULL;
