-- (tournament_id, tournament_user_id) 복합 인덱스로 교체한다.
-- 실제 쿼리가 두 컬럼을 동시에 필터하므로 단일 인덱스보다 복합 인덱스가 효율적이다.
ALTER TABLE tournament_histories
    DROP INDEX idx_tournament_histories_tournament_user_id,
    ADD INDEX idx_tournament_histories_tid_tuid (tournament_id, tournament_user_id);
