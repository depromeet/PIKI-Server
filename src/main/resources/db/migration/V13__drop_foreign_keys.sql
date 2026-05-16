-- 테이블 간 FK 제약을 두지 않고 논리적으로만 연결하는 정책에 따라 기존 FK 를 모두 제거한다.
ALTER TABLE tournament_wish_item DROP FOREIGN KEY fk_twi_tournament;
ALTER TABLE tournament DROP FOREIGN KEY fk_tournament_user;
ALTER TABLE tournament_user DROP FOREIGN KEY fk_tp_tournament;
ALTER TABLE tournament_user DROP FOREIGN KEY fk_tp_user;
