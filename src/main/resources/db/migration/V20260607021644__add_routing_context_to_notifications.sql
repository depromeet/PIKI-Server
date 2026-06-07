-- 파싱 알림(ITEM_PARSING_*) 딥링크 라우팅 컨텍스트 (#408).
-- 클라이언트는 kind 로 출처(위시/토너먼트)를 분기하고, TOURNAMENT 는 tournament_id(입장)·tournament_item_id(아이템 지목)로
-- 딥링크를 조립한다. 토너먼트 알림(TOURNAMENT_*)처럼 refId 만으로 충분한 알림은 세 컬럼 모두 NULL.
-- FK 없음(프로젝트 정책 — 참조 무결성은 애플리케이션이 책임). additive 라 out-of-order 무해.
ALTER TABLE notifications
    ADD COLUMN kind               VARCHAR(20) NULL,
    ADD COLUMN tournament_id      BIGINT      NULL,
    ADD COLUMN tournament_item_id BIGINT      NULL;
