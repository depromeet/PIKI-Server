-- 상품 버저닝 4a — items 의 추출 컬럼을 ItemSnapshot 으로 완전히 이전한 뒤 items 에는 정체성만 남긴다.
-- 추출값·상태는 전적으로 item_snapshots 가 보유하므로 items 에서 5개 컬럼을 제거한다.
-- items 에 남는 것은 정체성(source_url=link)과 공통 메타(id·created_at·updated_at·deleted_at)뿐이다.
-- FK 제약은 프로젝트 정책상 없어 제약 위반 위험이 없다. forward-only — 되돌리려면 보정 마이그레이션을 추가한다.
ALTER TABLE items
    DROP COLUMN name,
    DROP COLUMN image_url,
    DROP COLUMN current_price,
    DROP COLUMN currency,
    DROP COLUMN status;
