package com.depromeet.piki.notification.fcm.domain

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

// 사용자의 기기별 FCM 등록 정보 — 순수 발송 "주소록". 푸시 발송(#245)이 user_id 로 토큰을 모아 멀티캐스트한다.
// 기기 단위(user_id, device_id) 로 1 row 를 유지하며, fcm_token 은 그 기기의 (회전 가능한) 속성이다.
// device_id 는 iOS IDFV 등 설치당 안정적인 식별자. userId·fcmToken 은 재로그인/토큰 회전으로 갱신될 수 있어
// 가변. 배달 정확성을 위해 fcm_token 은 DB UNIQUE — 한 토큰은 한 사용자에게만 매핑된다.
// 알림 on/off 동의는 여기 두지 않는다 — OS 권한이 진짜 게이트고(클라만 읽음), 앱-레벨 음소거 선호가 실제로
// 필요해지면 user 레벨 선호로 별도 추가한다.
@Entity
@Table(name = "user_devices")
class UserDevice(
    userId: UUID,
    deviceId: String,
    fcmToken: String,
) : LongBaseEntity() {
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    var userId: UUID = userId
        protected set

    @Column(name = "device_id", nullable = false, length = MAX_DEVICE_ID_LENGTH)
    var deviceId: String = deviceId
        protected set

    @Column(name = "fcm_token", nullable = false, length = MAX_TOKEN_LENGTH)
    var fcmToken: String = fcmToken
        protected set

    // 엔티티 불변식 — 최후의 보루. 정상 흐름에선 입력 경계(#244 요청 DTO @NotBlank/@Size)가 먼저 거른다.
    // 누가 어떤 경로로 만들든 빈 값·초과 길이가 DB 저장 시점이 아니라 생성 시점에 깨지게 한다.
    // kotlin-jpa(noarg) 가 합성하는 JPA 생성자는 이 init 을 우회하므로 DB 로딩 행은 검증 대상이 아니다.
    init {
        validate(deviceId, fcmToken)
    }

    // 토큰 회전(#244) — 같은 사용자·기기의 토큰만 교체한다.
    fun refreshToken(fcmToken: String) {
        validate(deviceId, fcmToken)
        this.fcmToken = fcmToken
    }

    private fun validate(
        deviceId: String,
        fcmToken: String,
    ) {
        require(deviceId.isNotBlank()) { "기기 식별자가 비어 있습니다." }
        require(deviceId.length <= MAX_DEVICE_ID_LENGTH) { "기기 식별자 길이가 ${MAX_DEVICE_ID_LENGTH}자를 초과했습니다." }
        require(fcmToken.isNotBlank()) { "FCM 토큰이 비어 있습니다." }
        require(fcmToken.length <= MAX_TOKEN_LENGTH) { "FCM 토큰 길이가 ${MAX_TOKEN_LENGTH}자를 초과했습니다." }
    }

    companion object {
        // VARCHAR 컬럼과 엔티티 불변식이 같은 상수를 공유한다.
        const val MAX_DEVICE_ID_LENGTH = 255
        const val MAX_TOKEN_LENGTH = 512
    }
}
