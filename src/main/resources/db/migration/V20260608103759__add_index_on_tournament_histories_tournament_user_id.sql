-- tournament_histories.tournament_user_id: 참여자별 히스토리 조회 쿼리 지원
-- (tournament_id, tournament_user_id) 패턴으로 필터되므로 tournament_user_id 인덱스 추가
ALTER TABLE tournament_histories
    ADD INDEX idx_tournament_histories_tournament_user_id (tournament_user_id);
