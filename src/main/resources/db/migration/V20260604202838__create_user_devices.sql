-- user_devices 테이블 — 사용자의 기기별 FCM 등록 정보. 푸시 발송(#245)이 user_id 로 토큰을 모아 멀티캐스트한다.
-- FK 없음(프로젝트 정책 — 참조 무결성은 애플리케이션이 책임).
--
-- uk_fcm_token: 배달 정확성의 핵심 불변식. FCM 토큰은 앱 설치(=기기) 하나당 전역 유일하고 그게 곧 "어디로 쏘느냐".
--   한 토큰이 두 사용자 row 에 동시에 있으면 A 의 알림이 지금 그 기기를 쓰는 B 에게 가는 누출이 생긴다. 그래서
--   토큰 등록(#244)은 같은 토큰을 가진 기존 row 를 현재 사용자/기기로 재배정해 토큰당 1 row 를 유지한다.
--   (VARCHAR(512) utf8mb4 = 최대 2048 bytes < InnoDB 인덱스 키 한도 3072 bytes 라 UNIQUE 가능.)
-- uk_user_device: 한 사용자-기기당 1 row. 토큰 회전 시 새 row 를 쌓지 않고 UPDATE 한다. 복합 인덱스의 leftmost
--   가 user_id 라 발송 시 "이 사용자의 모든 기기" 조회(user_id 단독)도 함께 커버한다 → 별도 user_id 인덱스 불필요.
--
-- 인앱 알림 on/off 플래그는 두지 않는다. 알림 동의의 진짜 게이트는 OS 권한(설정앱)이고 그건 클라이언트만
-- 읽을 수 있다. OS 가 꺼져 있어도 FCM 발송은 성공 응답하고 iOS 가 표시만 안 하므로(에러 아님), 서버는 동의를
-- 추정·저장하지 않고 그냥 쏜다. 앱 삭제 등 진짜 죽은 토큰만 FCM 이 UNREGISTERED 로 알려줘 #245 가 정리한다.
-- (앱 자체 음소거 기능이 생기면 그때 push_enabled 를 additive 로 추가한다 — YAGNI.)
CREATE TABLE user_devices (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BINARY(16)   NOT NULL,
    device_id  VARCHAR(255) NOT NULL,
    fcm_token  VARCHAR(512) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    deleted_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_devices_fcm_token (fcm_token),
    UNIQUE KEY uk_user_devices_user_device (user_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
