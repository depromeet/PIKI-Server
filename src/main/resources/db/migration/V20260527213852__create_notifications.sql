-- notifications 테이블 — 알림 내역. title/body 는 발송 시점 템플릿 변수 치환이 끝난 완성본을 저장한다.
-- FK 없음(프로젝트 정책 — 참조 무결성은 애플리케이션이 책임). 조회 인덱스만 둔다.
-- (user_id, is_read) 복합 인덱스는 목록 조회(user_id leftmost)와 badge unread-count 집계를 함께 커버한다.
CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BINARY(16)   NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       VARCHAR(255) NOT NULL,
    ref_id     BIGINT       NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    deleted_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_user_id_is_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
