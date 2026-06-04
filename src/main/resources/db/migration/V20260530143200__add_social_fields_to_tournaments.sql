-- 소셜 토너먼트 기능: 초대 코드 · 초대 만료 시간 추가.
-- invite_code: 친구가 토너먼트 참여 시 입력하는 랜덤 6자리 숫자 코드.
-- invite_expires_at: 초대 링크 유효 기간. 이 시간 이후엔 새 참여자 합류 불가.
ALTER TABLE tournaments
    ADD COLUMN invite_code       VARCHAR(6)   NOT NULL DEFAULT '000000',
    ADD COLUMN invite_expires_at DATETIME(6)  NOT NULL DEFAULT '2000-01-01 00:00:00.000000';

-- 기존 레코드: 생성 시점 + 30분으로 backfill (이미 만료된 레코드로 간주해도 무방).
-- invite_code 는 각 행에 랜덤 6자리를 부여한다.
UPDATE tournaments
    SET invite_code        = LPAD(FLOOR(RAND() * 1000000), 6, '0'),
        invite_expires_at  = DATE_ADD(created_at, INTERVAL 30 MINUTE);
