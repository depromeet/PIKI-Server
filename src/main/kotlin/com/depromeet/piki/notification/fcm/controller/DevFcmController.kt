package com.depromeet.piki.notification.fcm.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.notification.fcm.controller.dto.DevPushRequest
import com.depromeet.piki.notification.fcm.controller.dto.DevPushResponse
import com.depromeet.piki.notification.fcm.service.FcmMessageSender
import jakarta.validation.Valid
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Profile
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// FE 가 Xcode 등에서 실시간으로 받은 FCM 토큰을 Postman 으로 우리 서버에 던져, 그 기기로 발송이 되는지
// 자가 확인하는 개발 전용 컨트롤러. @Profile("!prod") 라 운영에는 라우트 자체가 없다.
// 발송은 운영과 동일한 FcmMessageSender 를 그대로 태운다 — 이 API 로 본 동작이 곧 발송 경로의 동작이다.
// (수신자 정책(#236) 없이 "이 토큰으로 바로" 쏘므로 user_devices·이벤트 경로는 거치지 않는다.)
@Profile("!prod")
@RestController
@RequestMapping("/api/v1/dev/fcm")
class DevFcmController(
    private val fcmSenderProvider: ObjectProvider<FcmMessageSender>,
) : DevFcmApi {
    @PostMapping("/push")
    override fun push(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: DevPushRequest,
    ): ApiResponseBody<DevPushResponse> {
        val sender =
            fcmSenderProvider.getIfAvailable()
                ?: return ApiResponseBody.ok(DevPushResponse(fcmEnabled = false, staleTokenCount = 0))
        val result = sender.send(listOf(request.token.trim()), request.toNotification(userId))
        return ApiResponseBody.ok(DevPushResponse(fcmEnabled = true, staleTokenCount = result.staleTokens.size))
    }
}
