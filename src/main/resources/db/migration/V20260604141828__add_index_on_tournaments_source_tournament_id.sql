-- source_tournament_id: 중복 복제 검사(findBySourceTournamentId)와 그룹 결과 집계에 사용.
-- 인덱스 없으면 tournaments 풀스캔 발생.
ALTER TABLE tournaments
    ADD INDEX idx_tournaments_source_tournament_id (source_tournament_id);
