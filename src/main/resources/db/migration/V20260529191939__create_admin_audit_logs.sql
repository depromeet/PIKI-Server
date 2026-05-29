-- admin 백오피스가 실행한 작업의 감사 로그. append-only.
-- 정책: FK 없음(admin 계정은 InMemory 라 users 와 무관) / 테이블명 복수형 / utf8mb4.
-- created_at/updated_at/deleted_at 은 LongBaseEntity 컬럼 규약을 따른다(ddl-auto=validate 통과용).
CREATE TABLE admin_audit_logs (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    admin_username  VARCHAR(100)  NOT NULL COMMENT '실행 admin (InMemory 계정명)',
    action_type     VARCHAR(50)   NOT NULL COMMENT 'WRITE_COMMIT 등 작업 분류',
    tool_name       VARCHAR(100)  NOT NULL COMMENT '엔진이 호출한 tool 이름',
    result_status   VARCHAR(20)   NOT NULL COMMENT 'SUCCESS / FAILED',
    parameters      JSON          NULL COMMENT '마스킹된 파라미터(민감정보 제거 후)',
    result_summary  VARCHAR(1000) NULL COMMENT '결과 요약(원문 전체 저장 안 함)',
    request_message VARCHAR(2000) NULL COMMENT '사용자 자연어 요청(마스킹)',
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    deleted_at      DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_admin_audit_logs_admin_username (admin_username),
    KEY idx_admin_audit_logs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
