-- 회원 탈퇴 30일 파기 스케줄러(DailyAccountPurgeScheduler)가 콘텐츠 파기 완료를 표식으로 남겨,
-- 이미 파기한 tombstone 을 매일 다시 스캔하지 않게 한다(content_purged_at IS NULL 만 대상).
-- tombstone users 행 자체는 영구 보존하므로 deleted_at 만으로는 재스캔을 끊을 수 없어 별도 표식 컬럼이 필요하다.
-- additive(순서 무관), FK 없음. 스캔은 기존 idx_users_deleted_at 의 deleted_at 범위로 충분히 좁혀지므로
-- 별도 인덱스를 추가하지 않는다(파기된 행은 다음 스캔 결과집합에서 영구 제외되어 스캔 폭이 누적되지 않음).
ALTER TABLE users ADD COLUMN content_purged_at DATETIME(6) NULL;
