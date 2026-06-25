-- 알림 타입별 FCM 푸시 발송 여부를 DB 에서 동적 제어한다 — 정적 NotificationChannelPolicy.pushable() when 분기를 대체.
-- 백오피스(/admin/templates)에서 배포 없이 타입별 푸시 on/off 를 토글한다. 모든 알림은 SSE·알림센터로 항상 가고,
-- 타입별로 갈리는 건 "FCM 푸시까지 보내나" 하나뿐이라 컬럼 하나로 충분하다(announcements.push_enabled 와 동일 결).
-- DEFAULT TRUE 로 더한 뒤, 옛 when 분기에서 false 였던 두 타입(아이템 추가/삭제 — 인앱 SSE 로 충분)만 false 로 backfill 한다.
-- ADD COLUMN 은 additive 이고 backfill 은 같은 파일 안에서 그 직후 도므로 순서가 보장된다(이 컬럼은 이 PR 만 건드림). FK 제약 없음(프로젝트 규약).

ALTER TABLE notification_templates
    ADD COLUMN push_enabled BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE notification_templates
SET push_enabled = FALSE
WHERE type IN ('TOURNAMENT_ITEM_ADDED', 'TOURNAMENT_ITEM_DELETED');
