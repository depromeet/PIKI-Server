-- V3에서 추가된 컬럼이지만 현재 Tournament 엔티티에 매핑이 없어 INSERT 오류를 유발한다.
ALTER TABLE tournament
    DROP COLUMN round,
    DROP COLUMN final_winner_wish_item_id;
