-- admin 백오피스 기능 요청 수집함. 팀원이 "있으면 하는 admin 기능" 을 한 줄로 남기는 인박스.
-- 정책: FK 없음(admin 계정은 InMemory 라 users 와 무관) / 테이블명 복수형 / utf8mb4.
-- created_at/updated_at/deleted_at 은 LongBaseEntity 컬럼 규약을 따른다(ddl-auto=validate 통과용).
CREATE TABLE admin_feature_requests (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    title       VARCHAR(200) NOT NULL COMMENT '기능 요청 한 줄 제목',
    status      VARCHAR(16)  NOT NULL COMMENT 'NEW(접수) / DONE(검토 완료)',
    created_by  VARCHAR(100) NOT NULL COMMENT '작성 admin (InMemory 계정명)',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    deleted_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_admin_feature_requests_created_at (created_at),
    KEY idx_admin_feature_requests_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
