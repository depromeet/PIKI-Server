-- invite_code 는 랜덤 생성이라 낮은 확률로 충돌이 발생할 수 있다.
-- MySQL 은 partial unique index 를 지원하지 않으므로, generated column 으로 흉내낸다.
-- 활성 토너먼트(deleted_at IS NULL): active_invite_code = invite_code → 중복 차단
-- 삭제 토너먼트(deleted_at IS NOT NULL): active_invite_code = NULL → MySQL unique 는 NULL 여러 개 허용
ALTER TABLE tournaments
    ADD COLUMN active_invite_code VARCHAR(6) GENERATED ALWAYS AS (IF(deleted_at IS NULL, invite_code, NULL)) STORED,
    ADD UNIQUE KEY uk_tournaments_active_invite_code (active_invite_code);
