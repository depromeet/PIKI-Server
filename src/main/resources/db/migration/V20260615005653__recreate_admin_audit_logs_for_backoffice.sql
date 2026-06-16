-- prod 운영 백오피스(#249) 감사로그. admin 의 민감 작업(접근 허용·템플릿 수정·공지 발송 등) 기록.
-- 인증은 슬랙 원타임 링크 → IP allowlist + 세션(슬랙 신원) 모델이라 admin 계정 테이블(admin_users)은 두지 않는다.
-- 옛 dev 백오피스 테이블(admin_feature_requests·admin_audit_logs)은 #505 에서 DROP 됐고, 이건 새 prod 백오피스용.
-- FK 제약은 두지 않는다(프로젝트 규약).

CREATE TABLE admin_audit_logs (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    actor      VARCHAR(50)  NOT NULL,   -- 슬랙 표시명 (누가)
    action     VARCHAR(50)  NOT NULL,   -- ACCESS_GRANTED·ACCESS_REVOKED·TEMPLATE_UPDATE 등 (무엇)
    detail     VARCHAR(500) NOT NULL,   -- 사람이 읽는 상세 (민감 원본값 미포함)
    client_ip  VARCHAR(45)  NULL,       -- 어디서 (IPv6 최대 45자)
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    deleted_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_admin_audit_logs_created_at (created_at)   -- 최신순 조회용
);
