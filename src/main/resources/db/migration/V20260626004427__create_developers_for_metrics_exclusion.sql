-- 운영 통계 대시보드(/admin/metrics)의 "개발진 포함" 토글이 꺼져 있을 때(기본) 집계에서 제외할 개발진 명단.
-- user_id 로 보관한다 — email 은 user_details 에 이미 있으니 중복 저장하지 않고, users.id 를 논리 참조한다.
-- FK 는 두지 않는다(프로젝트 컨벤션 — 관계는 논리적으로만).
-- 추가는 이메일로 1회 해석해 넣으면 된다(개발진 본인은 user_id 를 몰라도 됨):
--   INSERT INTO developers (user_id) SELECT user_id FROM user_details WHERE email = 'alice@piki.day';
CREATE TABLE developers
(
    user_id    BINARY(16)   NOT NULL COMMENT 'UUID V4 — users.id 논리 참조(FK 미사용)',
    note       VARCHAR(100) NULL COMMENT '식별 메모(누구 계정인지)',
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '명단 추가 시각',
    PRIMARY KEY (user_id)
) COMMENT '운영 통계 대시보드 집계 제외 대상(개발진) 유저';
