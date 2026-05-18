package com.depromeet.team3.auth.controller

import com.depromeet.team3.auth.controller.dto.GuestCreateResponse
import com.depromeet.team3.auth.service.AuthService
import com.depromeet.team3.common.response.ApiResponseBody
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Profile("local", "dev")
@RestController
@RequestMapping("/dev")
class DevAuthController(
    private val authService: AuthService,
) : DevAuthApi {
    @PostMapping("/users/member")
    @ResponseStatus(HttpStatus.CREATED)
    override fun createDummyMember(
        @RequestParam(required = false) nickname: String?,
    ): ApiResponseBody<GuestCreateResponse> {
        val resolvedNickname = nickname ?: "테스트유저_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val tokenPair = authService.createMember(resolvedNickname)
        return ApiResponseBody.created(GuestCreateResponse.from(tokenPair))
    }
}
