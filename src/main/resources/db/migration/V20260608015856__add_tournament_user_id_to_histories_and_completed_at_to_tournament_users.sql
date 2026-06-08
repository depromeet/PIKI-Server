-- tournament_histories: 참여자별 독립 진행을 위해 어느 TU의 매치인지 추적한다.
-- nullable: 기존 행 보존, 신규 행은 항상 값이 있다.
ALTER TABLE tournament_histories
    ADD COLUMN tournament_user_id BIGINT NULL;

-- tournament_users: 개인 완료 시점 추적. 모두 완료될 때만 Tournament 상태가 COMPLETED로 전환된다.
ALTER TABLE tournament_users
    ADD COLUMN completed_at DATETIME(6) NULL;
