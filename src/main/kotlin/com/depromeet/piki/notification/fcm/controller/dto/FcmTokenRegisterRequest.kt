package com.depromeet.piki.notification.fcm.controller.dto

import com.depromeet.piki.notification.fcm.domain.UserDevice
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "FCM 토큰 등록 요청 (로그인·알림설정 ON·토큰 갱신 시 호출)")
data class FcmTokenRegisterRequest(
    @field:NotBlank(message = BLANK_MESSAGE)
    @field:Size(max = UserDevice.MAX_TOKEN_LENGTH)
    @field:Schema(
        description = "FCM 등록 토큰 (Firebase iOS SDK 가 발급)",
        example = "fGcServerTokenSample:APA91bF...",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val token: String,
    @field:NotBlank(message = BLANK_MESSAGE)
    @field:Size(max = UserDevice.MAX_DEVICE_ID_LENGTH)
    @field:Schema(
        description = "기기 식별자 (iOS IDFV — UIDevice.current.identifierForVendor)",
        example = "1B2A3C4D-5E6F-7A8B-9C0D-1E2F3A4B5C6D",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val deviceId: String,
) {
    // 응답 detail 은 사용자 대면이라 친화 문구로 둔다. token·deviceId 누락은 사용자에겐 같은 안내라 한 상수를 공유한다
    // (어느 필드가 비었는지는 앱이 자기 요청으로 안다). OpenApiExamples 도 이 상수를 참조해 문서·응답을 일치시킨다.
    companion object {
        const val BLANK_MESSAGE = "알림 설정에 실패했어요. 잠시 후 다시 시도해 주세요."
    }
}
