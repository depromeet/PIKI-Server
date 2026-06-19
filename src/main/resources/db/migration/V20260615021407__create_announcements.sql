-- 공지 발송 아카이브(#391/#489) — 백오피스에서 작성·발송한 전체 공지의 원본·발송 내역.
-- 알림센터 fan-out(유저별 ANNOUNCEMENT 알림 생성)은 #489 가 다루고, 이 테이블은 발송 원본·이력의 단일 출처다.
-- FK 제약은 두지 않는다(프로젝트 규약).
CREATE TABLE announcements (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    title           VARCHAR(255) NOT NULL,
    body            VARCHAR(1000) NOT NULL DEFAULT '',
    target          VARCHAR(50)  NOT NULL,   -- 발송 대상 라벨 (예: 토큰 보유자 전체)
    recipient_count INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL,   -- DRAFT·SENT
    sent_at         DATETIME(6)  NULL,
    sent_by         VARCHAR(50)  NULL,       -- 발송한 운영자 (등록 시점엔 null)
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_announcements_created_at (created_at)
);
