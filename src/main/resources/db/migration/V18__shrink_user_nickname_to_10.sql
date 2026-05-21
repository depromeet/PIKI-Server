-- 기획 반영: 닉네임 정책 16자 → 10자.
-- 사전 검증 (2026-05-21 dev RDS): 11자 초과 닉네임 0건.
--   SELECT COUNT(*) FROM `user` WHERE CHAR_LENGTH(nickname) > 10;  → 0
-- 신규 입력 가드: GUEST 닉네임 생성은 prefix + animal 조합으로 최대 9자,
-- MEMBER 도 @field:Size(max=10) 으로 막힘. 기존 데이터 잘림 위험 없음 확인.
ALTER TABLE `user` MODIFY COLUMN nickname VARCHAR(10) NOT NULL;
