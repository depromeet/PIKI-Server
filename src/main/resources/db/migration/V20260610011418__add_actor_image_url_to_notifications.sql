-- 알림 행위자(actor) 프로필 사진 snapshot 컬럼 (#473).
-- 사람 알림(TOURNAMENT_* 등 actor 있는 알림)은 발송 시점 actor 의 프로필 이미지 URL 을 여기 박아 고정한다.
-- 이후 actor 가 프사를 바꿔도 과거 알림은 그 시점 사진 그대로 유지된다(title 의 닉네임 snapshot 과 같은 결).
-- 프로필 이미지 URL 은 업로드마다 새 키(immutable)라 옛 URL 이 옛 이미지를 계속 가리켜 snapshot 이 안전하다.
-- 시스템 알림(actor 없음)은 NULL — 직렬화 시 서버가 defaultPushImg(피키 로고)로 채운다(컬럼엔 저장 안 함).
-- length 2048 은 users.profile_image 와 정합. additive 라 out-of-order 무해. FK 없음(프로젝트 정책).
ALTER TABLE notifications
    ADD COLUMN actor_image_url VARCHAR(2048) NULL;
