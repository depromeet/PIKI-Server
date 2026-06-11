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
    // FCM 토큰·기기 식별자는 앱이 SDK 에서 받아 보내는 값이라 엔드유저 비대면(앱 구현 영역)이다.
    // 위반은 앱 버그이므로 사용자 친화 문구가 아니라 어떤 필드가 비었는지 짚는 개발자용 메시지로 둔다.
    // OpenApiExamples 가 같은 상수를 참조해 문서 detail 과 실제 응답 detail 을 일치시킨다.
    companion object {
        const val TOKEN_BLANK_MESSAGE = "FCM 토큰은 비어 있을 수 없습니다."
        const val DEVICE_ID_BLANK_MESSAGE = "기기 식별자는 비어 있을 수 없습니다."
    }
}
