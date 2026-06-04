-- V20260530143200 backfill 에서 숫자 6자리(예: 042837)로 채워진 invite_code 를
-- 현재 포맷(대문자 영어 3자리 + 숫자 3자리, 예: ABC123)으로 정정한다.
-- 대상 레코드는 모두 invite_expires_at 이 초과된 상태라 운영 영향 없음.
UPDATE tournaments
    SET invite_code = CONCAT(
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZ', FLOOR(1 + RAND() * 26), 1),
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZ', FLOOR(1 + RAND() * 26), 1),
        SUBSTRING('ABCDEFGHIJKLMNOPQRSTUVWXYZ', FLOOR(1 + RAND() * 26), 1),
        LPAD(FLOOR(RAND() * 1000), 3, '0')
    )
WHERE invite_code REGEXP '^[0-9]{6}$';
