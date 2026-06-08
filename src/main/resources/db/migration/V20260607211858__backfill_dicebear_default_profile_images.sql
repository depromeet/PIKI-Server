-- 기존 dicebear 기본 아바타 유저를 새 S3 기본 아바타(4종 랜덤)로 backfill 한다.
-- 신규 생성 로직의 dicebear 제거(이 PR)와 한 세트 — 배포 시 Flyway 가 각 env RDS 에 자동 적용한다.
-- 대상: profile_image 가 dicebear 인 활성 유저만. 실제 프사(OAuth·업로드)와 탈퇴 tombstone(deleted_at)은 제외.
-- URL 은 env 별 publicBaseUrl(placeholder) 로 조립해 DefaultProfileImages 와 동일 포맷(trailing-slash 제거 + /user-profile-{n}.png)을 맞춘다.
-- RAND() 는 행마다 평가돼 4종에 분산. 배포 후 dicebear 생성이 없어 재실행해도 no-op. publicBaseUrl 미설정(로컬/테스트)이면 가드로 no-op.
UPDATE users
SET profile_image = CONCAT(TRIM(TRAILING '/' FROM '${s3publicbaseurl}'),
                           '/user-profile-', FLOOR(1 + RAND() * 4), '.png')
WHERE profile_image LIKE 'https://api.dicebear.com/%'
  AND deleted_at IS NULL
  AND '${s3publicbaseurl}' <> '';
