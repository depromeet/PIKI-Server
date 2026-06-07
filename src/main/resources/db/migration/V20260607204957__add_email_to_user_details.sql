-- 소셜 로그인 이메일 수집 재도입 (#442). #122 진행 전 V20260527011356 에서 drop 했던 email 컬럼을
-- 마케팅·서비스 알림·계정 복구 용도로 재추가한다. 구글·애플에서 수집하며 nullable 이다
-- (애플 Private Relay 거부·카카오 미수집·기존 가입자는 null). 탈퇴 시 user_details 하드삭제로 함께 파기된다.
-- unique 제약 없음 — email 은 식별자가 아니라 부가 정보다(한 사람이 소셜별로 다른 email 일 수 있음).
ALTER TABLE user_details ADD COLUMN email VARCHAR(255) NULL;
