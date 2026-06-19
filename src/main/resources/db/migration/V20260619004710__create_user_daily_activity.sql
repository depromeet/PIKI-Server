-- 런칭데이 리텐션·DAU 측정용 일별 활성 유저 기록. 인증된 요청이 닿을 때 (user_id, 그날 KST) 1행을 남긴다.
-- 분석 종료 후 DROP 예정(forward-only). FK 없음(컨벤션). 복합 PK 가 유니크 + (user_id) prefix 조회를 겸하고,
-- 날짜 단위 집계(DAU·리텐션)를 위해 active_date 보조 인덱스를 둔다.
CREATE TABLE user_daily_activity (
    user_id     BINARY(16)  NOT NULL,
    active_date DATE        NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, active_date),
    KEY idx_user_daily_activity_active_date (active_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
