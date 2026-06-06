package com.depromeet.piki.notification.fcm.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "[DEV] FCM 발송 결과 — 실제 배달 여부 디버깅용")
data class DevPushResponse(
    @field:Schema(
        description = "FCM 활성 여부 — false 면 FIREBASE_SERVICE_ACCOUNT 미설정이라 발송이 no-op (이 서버에 키가 없음)",
    )
    val fcmEnabled: Boolean,
    @field:Schema(
        description = "FCM 이 무효(UNREGISTERED)로 응답해 정리 대상이 된 토큰 수 — 1 이면 그 토큰이 죽음/만료(앱 삭제 등)",
    )
    val staleTokenCount: Int,
)
