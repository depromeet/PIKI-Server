package com.depromeet.piki.notification.fcm.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.notification.fcm.controller.dto.FcmDeviceUnregisterRequest
import com.depromeet.piki.notification.fcm.controller.dto.FcmTokenRegisterRequest
import com.depromeet.piki.notification.fcm.service.UserDeviceService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/fcm/tokens")
class FcmTokenController(
    private val userDeviceService: UserDeviceService,
) : FcmTokenApi {
    @PostMapping
    override fun register(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: FcmTokenRegisterRequest,
    ): ApiResponseBody<Unit> {
        userDeviceService.register(userId = userId, deviceId = request.deviceId, fcmToken = request.token)
        return ApiResponseBody.ok()
    }

    @DeleteMapping
    override fun unregister(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: FcmDeviceUnregisterRequest,
    ): ApiResponseBody<Unit> {
        userDeviceService.unregister(userId = userId, deviceId = request.deviceId)
        return ApiResponseBody.ok()
    }
}
