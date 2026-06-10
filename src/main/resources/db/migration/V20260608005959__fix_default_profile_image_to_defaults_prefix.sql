-- 기본 아바타 키 보정: 루트(/user-profile-{n}.png) → defaults/(/defaults/user-profile-{n}.png).
-- 1차 backfill(V20260607211858)이 dicebear 유저를 루트 키로 옮겼으나 실제 S3 객체는 defaults/ 아래라
-- Access Denied(없는 키 → 익명 403) 였다. 이미 적용된 V20260607211858 은 checksum 때문에 수정 금지 → 별도 보정.
--   dev : 1차 적용 완료 → 이 마이그레이션이 루트→defaults 재지정.
--   prod: 1차(dicebear→루트) 적용 후 이 보정(루트→defaults)이 이어져 최종 defaults 로 수렴.
-- 멱등: 이미 defaults/ 인 행은 NOT LIKE 가드로 제외. 실제 프사(OAuth/업로드)·탈퇴 tombstone 은 /user-profile- 패턴이 없어 미해당.
UPDATE users
SET profile_image = REPLACE(profile_image, '/user-profile-', '/defaults/user-profile-')
WHERE profile_image LIKE '%/user-profile-%'
  AND profile_image NOT LIKE '%/defaults/user-profile-%'
  AND deleted_at IS NULL;
