package com.depromeet.piki.notification.fcm.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "[DEV] FCM 발송 결과 — 실제 배달 여부를 디버깅하기 위한 메타")
data class DevPushResponse(
    @field:Schema(description = "발송을 시도한 이 유저의 등록 기기 토큰 수 (0 이면 등록된 기기가 없어 발송이 일어나지 않음)")
    val targetTokenCount: Int,
    @field:Schema(
        description = "FCM 활성 여부 — false 면 FIREBASE_SERVICE_ACCOUNT 미설정이라 발송이 no-op (앱은 200 을 응답)",
    )
    val fcmEnabled: Boolean,
)
