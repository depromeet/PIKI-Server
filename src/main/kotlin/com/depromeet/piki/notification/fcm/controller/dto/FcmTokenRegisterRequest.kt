package com.depromeet.piki.notification.fcm.controller.dto

import com.depromeet.piki.notification.fcm.domain.UserDevice
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "FCM 토큰 등록 요청 (로그인·알림설정 ON·토큰 갱신 시 호출)")
data class FcmTokenRegisterRequest(
    @field:NotBlank
    @field:Size(max = UserDevice.MAX_TOKEN_LENGTH)
    @field:Schema(
        description = "FCM 등록 토큰 (Firebase iOS SDK 가 발급)",
        example = "fGcServerTokenSample:APA91bF...",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val token: String,
    @field:NotBlank
    @field:Size(max = UserDevice.MAX_DEVICE_ID_LENGTH)
    @field:Schema(
        description = "기기 식별자 (iOS IDFV — UIDevice.current.identifierForVendor)",
        example = "1B2A3C4D-5E6F-7A8B-9C0D-1E2F3A4B5C6D",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val deviceId: String,
)
