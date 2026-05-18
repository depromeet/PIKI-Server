package com.depromeet.team3.auth.controller

import com.depromeet.team3.auth.controller.dto.GuestCreateResponse
import com.depromeet.team3.auth.service.AuthService
import com.depromeet.team3.common.response.ApiResponseBody
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Profile("local", "dev")
@Tag(name = "Dev", description = "개발 전용 API — Task 6 OAuth 완료 후 제거 예정")
@RestController
@RequestMapping("/dev")
class DevAuthController(
    private val authService: AuthService,
) {
    @Operation(summary = "더미 MEMBER 생성", description = "OAuth 없이 MEMBER 유저를 생성하고 토큰을 발급한다. local/dev 환경 전용.")
    @PostMapping("/users/member")
    @ResponseStatus(HttpStatus.CREATED)
    fun createDummyMember(
        @RequestParam(required = false) nickname: String?,
    ): ApiResponseBody<GuestCreateResponse> {
        val resolvedNickname = nickname ?: "테스트유저_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val tokenPair = authService.createMember(resolvedNickname)
        return ApiResponseBody.created(GuestCreateResponse.from(tokenPair))
    }
}
