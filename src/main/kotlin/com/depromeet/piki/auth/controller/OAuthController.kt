package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.controller.dto.OAuthLoginRequest
import com.depromeet.piki.auth.controller.dto.OAuthLoginResponse
import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.service.OAuthLoginService
import com.depromeet.piki.common.response.ApiResponseBody
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth/login")
class OAuthController(
    private val oAuthLoginService: OAuthLoginService,
) : OAuthApi {
    private val log = LoggerFactory.getLogger(javaClass)

    // currentUserId 는 게스트 토큰을 함께 보낸 경우에만 채워진다(permitAll 이라 토큰 없어도 진입). 있으면 게스트-연결 흐름.
    @PostMapping("/{provider}")
    override fun login(
        @PathVariable provider: String,
        @Valid @RequestBody request: OAuthLoginRequest,
        @AuthenticationPrincipal currentUserId: UUID?,
    ): ApiResponseBody<OAuthLoginResponse> {
        val oAuthProvider = OAuthProvider.from(provider)
        log.info("소셜 로그인 요청: provider={}, 게스트연결={}", oAuthProvider, currentUserId?.let { "있음" } ?: "없음")
        val result = oAuthLoginService.login(oAuthProvider, request.toCommand(), currentUserId)
        return ApiResponseBody.ok(OAuthLoginResponse.from(result.tokenPair, result.user))
    }
}
