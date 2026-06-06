package com.depromeet.piki.notification.fcm.controller.dto

import com.depromeet.piki.notification.fcm.domain.UserDevice
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "FCM 기기 해제 요청 (로그아웃 시 호출 — /auth/logout 보다 먼저)")
data class FcmDeviceUnregisterRequest(
    @field:NotBlank
    @field:Size(max = UserDevice.MAX_DEVICE_ID_LENGTH)
    @field:Schema(
        description = "해제할 기기 식별자 (iOS IDFV)",
        example = "1B2A3C4D-5E6F-7A8B-9C0D-1E2F3A4B5C6D",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val deviceId: String,
)
