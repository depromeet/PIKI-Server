package com.depromeet.piki.notification.fcm.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.notification.fcm.controller.dto.DevPushRequest
import com.depromeet.piki.notification.fcm.controller.dto.DevPushResponse
import com.depromeet.piki.notification.fcm.service.FcmMessageSender
import com.depromeet.piki.notification.fcm.service.UserDeviceService
import com.depromeet.piki.notification.service.PushNotificationChannel
import jakarta.validation.Valid
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Profile
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// FCM 발송 경로(#245)를 수신자 정책(#236) 없이 직접 태워보는 개발 전용 컨트롤러. 운영(prod)에는 뜨지 않는다.
// 발송은 PushNotificationChannel.send 를 그대로 재사용한다 — 테스트 경로와 실제 이벤트 경로가 같은 발송 코드를
// 통과하므로, 이 API 로 검증한 동작이 곧 이벤트 경로(#236 풀린 뒤)의 동작이다.
@Profile("!prod")
@RestController
@RequestMapping("/api/v1/dev/fcm")
class DevFcmController(
    private val pushNotificationChannel: PushNotificationChannel,
    private val userDeviceService: UserDeviceService,
    private val fcmSenderProvider: ObjectProvider<FcmMessageSender>,
) : DevFcmApi {
    @PostMapping("/push")
    override fun push(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: DevPushRequest,
    ): ApiResponseBody<DevPushResponse> {
        val targetTokenCount = userDeviceService.findTokens(userId).size
        val fcmEnabled = fcmSenderProvider.getIfAvailable()?.let { true } ?: false
        pushNotificationChannel.send(userId, request.toNotification(userId))
        return ApiResponseBody.ok(DevPushResponse(targetTokenCount = targetTokenCount, fcmEnabled = fcmEnabled))
    }
}
