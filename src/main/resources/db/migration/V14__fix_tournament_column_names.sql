-- tournament.user_id(BINARY 16, userId)를
-- owner_tournament_user_id(BIGINT, TournamentUser.id)로 변경.
-- 타입이 달라 기존 데이터의 의미있는 변환이 불가능하므로 0으로 초기화한다.
UPDATE tournament SET user_id = 0x00000000000000000000000000000000;
ALTER TABLE tournament
    CHANGE COLUMN user_id owner_tournament_user_id BIGINT NOT NULL;

-- tournament_history 컬럼명을 wish_item 기반에서 tournament_item 기반으로 통일한다.
ALTER TABLE tournament_history
    RENAME COLUMN first_wish_item_id TO first_tournament_item_id;
ALTER TABLE tournament_history
    RENAME COLUMN second_wish_item_id TO second_tournament_item_id;
ALTER TABLE tournament_history
    RENAME COLUMN winner_wish_item_id TO selected_tournament_item_id;
