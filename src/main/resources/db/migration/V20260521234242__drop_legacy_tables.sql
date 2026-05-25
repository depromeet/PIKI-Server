-- 옛 단수형 스키마 + obsolete 테이블 정리. 다음 마이그레이션이 복수형 통일 스키마를 새로 만든다.
-- IF EXISTS 로 두어 빈 DB / 일부만 적용된 DB 어디서 돌아도 안전.

DROP TABLE IF EXISTS tournament_wish_item;
DROP TABLE IF EXISTS tournament_participant;
DROP TABLE IF EXISTS tournament_history;
DROP TABLE IF EXISTS tournament_item;
DROP TABLE IF EXISTS tournament_user;
DROP TABLE IF EXISTS tournament;
DROP TABLE IF EXISTS wishes;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS user_detail;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS guests;
