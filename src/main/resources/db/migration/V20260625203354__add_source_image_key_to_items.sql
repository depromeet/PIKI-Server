-- 이미지 등록 outbox화 — 이미지 입력(S3 raw object key)을 durable 하게 items 에 적재해 link 처럼 outbox 재시도에 태운다.
-- source_url(link)과 대칭되는 입력 정체성 컬럼: 이미지 등록 경로는 source_url 이 null 이고 이 컬럼이 채워진다(둘 중 하나).
-- 길이 1024 는 S3 object key 상한(1024 바이트)에 맞춘다. additive — FK 없음(프로젝트 정책). forward-only.
ALTER TABLE items ADD COLUMN source_image_key VARCHAR(1024) NULL AFTER source_url;
