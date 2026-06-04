-- 소셜 토너먼트 플레이 링크 기능: 완료된 토너먼트의 아이템 구성을 공유해 친구들이 동일하게 진행할 수 있도록 한다.
-- play_link_expires_at: 플레이 링크 유효 기간. NULL이면 플레이 링크 미생성 상태.
-- source_tournament_id: 플레이 링크로 복제된 경우 원본 토너먼트 ID. NULL이면 원본.
ALTER TABLE tournaments
    ADD COLUMN play_link_expires_at DATETIME(6) NULL,
    ADD COLUMN source_tournament_id BIGINT       NULL;
