package com.depromeet.piki.notification.fcm.controller.dto

import com.depromeet.piki.notification.fcm.domain.UserDevice
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "FCM 토큰 등록 요청 (로그인·알림설정 ON·토큰 갱신 시 호출)")
data class FcmTokenRegisterRequest(
    @field:NotBlank(message = TOKEN_BLANK_MESSAGE)
    @field:Size(max = UserDevice.MAX_TOKEN_LENGTH)
    @field:Schema(
        description = "FCM 등록 토큰 (Firebase iOS SDK 가 발급)",
        example = "fGcServerTokenSample:APA91bF...",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val token: String,
    @field:NotBlank(message = DEVICE_ID_BLANK_MESSAGE)
    @field:Size(max = UserDevice.MAX_DEVICE_ID_LENGTH)
    @field:Schema(
        description = "기기 식별자 (iOS IDFV — UIDevice.current.identifierForVendor)",
        example = "1B2A3C4D-5E6F-7A8B-9C0D-1E2F3A4B5C6D",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val deviceId: String,
) {
    // Bean Validation 위반 메시지의 single source. OpenAPI example(FcmTokenApiExamples)이 같은 상수를 참조해
    // 문서의 detail 과 실제 응답 detail 이 어긋나지 않게 한다.
    companion object {
        const val TOKEN_BLANK_MESSAGE = "FCM 토큰이 비어 있어요."
        const val DEVICE_ID_BLANK_MESSAGE = "기기 식별자가 비어 있어요."
    }
}
