package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.controller.dto.GuestCreateResponse
import com.depromeet.piki.auth.controller.dto.LogoutResponse
import com.depromeet.piki.auth.controller.dto.TokenRefreshRequest
import com.depromeet.piki.auth.controller.dto.TokenRefreshResponse
import com.depromeet.piki.auth.exception.AuthException
import com.depromeet.piki.auth.service.AuthService
import com.depromeet.piki.auth.web.TokenCookieWriter
import com.depromeet.piki.common.response.ApiResponseBody
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) : AuthApi {
    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    override fun createGuest(): ApiResponseBody<GuestCreateResponse> {
        val result = authService.createGuest()
        return ApiResponseBody.created(GuestCreateResponse.from(result.tokenPair, result.user))
    }

    // refresh 토큰을 쿠키(WEB) 또는 body(APP) 어느 쪽에서든 받는다. 둘 다 없으면 400.
    // 쿠키 정책(HttpOnly·SameSite 등)은 TokenCookieWriter/advice 가 소유하고, 여기선 입력만 읽는다.
    @PostMapping("/token/refresh")
    override fun refresh(
        @Valid @RequestBody(required = false) request: TokenRefreshRequest?,
        @CookieValue(name = TokenCookieWriter.REFRESH_COOKIE, required = false) cookieRefreshToken: String?,
    ): ApiResponseBody<TokenRefreshResponse> {
        val refreshToken = cookieRefreshToken ?: request?.refreshToken ?: throw AuthException.refreshTokenRequired()
        val tokenPair = authService.refresh(refreshToken)
        return ApiResponseBody.ok(TokenRefreshResponse.from(tokenPair))
    }

    @PostMapping("/logout")
    override fun logout(
        @AuthenticationPrincipal userId: UUID,
    ): ApiResponseBody<LogoutResponse> {
        authService.logout(userId)
        return ApiResponseBody.ok(LogoutResponse())
    }
}
