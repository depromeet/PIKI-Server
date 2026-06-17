-- 알림 문구 템플릿을 DB 에서 관리(#252) — 백오피스(#250)에서 배포 없이 title/body 수정. type 이 자연키(PK).
-- 변수 플레이스홀더(dollar-brace 표기)는 발송 시점에 핸들러가 채운 변수로 치환된다(렌더 결과는 Notification row 에 freeze).
-- 사용 가능 변수 카탈로그는 코드가 SSOT 라 DB 엔 두지 않고, 편집 대상(title/body)만 둔다. FK 제약 없음(프로젝트 규약).
-- 시드는 별도 Java 마이그레이션(V20260615015148)으로 넣는다 — 템플릿 본문의 리터럴 dollar-brace 를 Flyway 가
-- placeholder 로 오인해 SQL 파싱이 깨지므로, JDBC 로 직접 적재한다.

CREATE TABLE notification_templates (
    type           VARCHAR(64)  NOT NULL,
    title_template VARCHAR(255) NOT NULL,
    body_template  VARCHAR(500) NOT NULL DEFAULT '',
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (type)
);
