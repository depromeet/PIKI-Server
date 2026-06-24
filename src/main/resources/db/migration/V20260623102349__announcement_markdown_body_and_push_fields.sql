-- 공지 패치노트화(#561) — body 를 마크다운 장문으로(VARCHAR(1000)→TEXT) 바꾸고, 푸시 전용 필드를 분리한다.
--
-- 왜 push_* 분리: notifications.body 가 VARCHAR(255) 라, 공지 body 를 그대로 알림에 넣으면 255 초과 시 broadcast 가
--   require 위반으로 깨진다(잠재 버그). 푸시·알림센터엔 push_title·push_body(≤255), 공지 페이지엔 full body(마크다운).
--   push_enabled 로 FCM 인터럽트 여부를 토글한다(공지는 페이지·알림센터엔 항상, 푸시만 선택).
--
-- 전부 additive(ADD COLUMN) + body 위닝(widening: VARCHAR→TEXT)이라 적용 순서와 무관하다(out-of-order 안전). FK 없음(규약).
-- body 는 TEXT 라 리터럴 DEFAULT 를 못 두므로 기존 DEFAULT '' 를 제거한다 — 앱(Announcement 생성자)이 body 를 항상 채운다.
ALTER TABLE announcements
    MODIFY body TEXT NOT NULL,
    ADD COLUMN push_enabled BOOLEAN      NOT NULL DEFAULT TRUE,  -- FCM 인터럽트 여부. 기존 공지는 항상 푸시됐으므로 TRUE.
    ADD COLUMN push_title   VARCHAR(255) NOT NULL DEFAULT '',    -- push on 일 때 FCM/알림센터 title (기본=공지 title, 편집 가능)
    ADD COLUMN push_body    VARCHAR(255) NOT NULL DEFAULT '';    -- push on 일 때 FCM/알림센터 body (빈값 허용)
