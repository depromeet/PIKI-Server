-- admin 백오피스 기능 전체 제거(코드·라우트·설정 삭제)에 따라 더 이상 쓰이지 않는 테이블을 떨군다.
-- 두 테이블 모두 dev 전용 백오피스가 쓰던 것으로, 운영/스테이징엔 admin 기능이 떠 본 적이 없어 데이터가 없다.
-- create 마이그레이션(V20260529191939, V20260531213259)은 적용 이력 보존을 위해 그대로 두고(적용된 파일 삭제 금지 규약),
-- forward-only 로 이 보정 마이그레이션이 테이블을 제거한다. FK 가 없어 단순 DROP 으로 충분하다.
DROP TABLE IF EXISTS admin_feature_requests;
DROP TABLE IF EXISTS admin_audit_logs;
