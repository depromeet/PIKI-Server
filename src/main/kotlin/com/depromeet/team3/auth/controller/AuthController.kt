package com.depromeet.team3.auth.controller

import com.depromeet.team3.auth.controller.dto.GuestCreateResponse
import com.depromeet.team3.auth.controller.dto.TokenRefreshRequest
import com.depromeet.team3.auth.controller.dto.TokenRefreshResponse
import com.depromeet.team3.auth.service.AuthService
import com.depromeet.team3.common.response.ApiResponseBody
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
) : AuthApi {
    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    override fun createGuest(): ApiResponseBody<GuestCreateResponse> {
        val tokenPair = authService.createGuest()
        return ApiResponseBody.created(GuestCreateResponse.from(tokenPair))
    }

    @PostMapping("/token/refresh")
    override fun refresh(
        @Valid @RequestBody request: TokenRefreshRequest,
    ): ApiResponseBody<TokenRefreshResponse> {
        val tokenPair = authService.refresh(request.refreshToken)
        return ApiResponseBody.ok(TokenRefreshResponse.from(tokenPair))
    }

    @PostMapping("/logout")
    override fun logout(
        @AuthenticationPrincipal userId: UUID,
    ): ApiResponseBody<Nothing> {
        authService.logout(userId)
        return ApiResponseBody.ok()
    }
}
