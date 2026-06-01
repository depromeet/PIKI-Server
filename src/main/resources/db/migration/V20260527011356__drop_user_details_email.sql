-- OAuth 통합 (epic #122) 진행 전 schema 정합. email 결을 OAuth 에서 받지 않기로 결정한 정합:
--   - Kakao 이메일 동의 항목 받으면 검수 1-2주 → 동의 항목 0 으로 면제
--   - Google·Apple 도 email scope 빼면 검수 결 가벼움
--   - 마케팅·인증·복구 메일 어떤 용도로도 안 받음 — dead column
-- DB 영향 0 (user_details 행 0 rows empirical 확인). 미래 필요 시 ADD COLUMN 으로 재추가 (backfill 0).

ALTER TABLE user_details DROP COLUMN email;
