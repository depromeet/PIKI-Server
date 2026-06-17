-- 공지 발송 건별 결과(#489). 수신자(userId) 1명당 1행으로 FCM 전송 결과를 남겨,
-- 추후 notifications.is_read(클릭/읽음)와 user_id+announcement_id 로 JOIN 해 "전송 성공 대비 클릭" 퍼널 분석을 가능케 한다.
-- UI 에는 이 행들을 집계(성공 수·코드별 실패 수)해서만 노출한다. FK 제약은 두지 않는다(논리적 참조, 조회 인덱스만).
CREATE TABLE announcement_deliveries (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    announcement_id BIGINT       NOT NULL,
    user_id         BINARY(16)   NOT NULL,
    -- SUCCESS(1개 이상 기기 도달) · FAILED(전 기기 실패) · NO_TOKEN(토큰 없음) · SKIPPED(FCM 미설정 환경)
    status          VARCHAR(20)  NOT NULL,
    -- 실패 시 FCM messagingErrorCode(UNREGISTERED·SENDER_ID_MISMATCH 등). 성공·토큰없음은 NULL.
    fcm_code        VARCHAR(50)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    -- BaseEntity 가 매핑하는 soft-delete 컬럼. 없으면 insert/select 시 컬럼 불일치로 실패한다(다른 테이블과 동일 관례).
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    -- 수신자 1명당 1행(파일 상단 계약)을 DB 가 강제한다. 재시도·중복 실행 시 같은 유저 행이 쌓여 집계(성공·코드별 실패 수)가 부풀려지는 걸 막는다.
    UNIQUE KEY uk_announcement_deliveries_announcement_user (announcement_id, user_id),
    KEY idx_announcement_deliveries_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
