package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.service.AppleNotificationService
import com.depromeet.piki.common.response.ApiResponseBody
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/apple")
class AppleNotificationController(
    private val appleNotificationService: AppleNotificationService,
) : AppleNotificationApi {
    // Apple 은 application/x-www-form-urlencoded 의 payload 필드에 서명된 JWT 를 담아 POST 한다.
    @PostMapping("/notifications", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    override fun handle(
        @RequestParam("payload") payload: String,
    ): ApiResponseBody<Unit> {
        appleNotificationService.handle(payload)
        return ApiResponseBody.ok()
    }
}
