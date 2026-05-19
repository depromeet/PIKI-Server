package com.depromeet.team3.auth.controller

import com.depromeet.team3.auth.controller.dto.DevUserCreateRequest
import com.depromeet.team3.auth.controller.dto.GuestCreateResponse
import com.depromeet.team3.auth.exception.AuthException
import com.depromeet.team3.auth.service.AuthService
import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.user.domain.IdentityType
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Profile("dev", "local")
@RestController
@RequestMapping("/dev")
class DevAuthController(
    private val authService: AuthService,
) : DevAuthApi {
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    override fun createDevUser(
        @Valid @RequestBody request: DevUserCreateRequest,
    ): ApiResponseBody<GuestCreateResponse> {
        val tokenPair =
            when (request.identityType) {
                IdentityType.GUEST -> authService.createGuest()
                IdentityType.MEMBER -> {
                    val nickname = request.nickname ?: throw AuthException.missingNickname()
                    authService.createMember(nickname)
                }
            }
        return ApiResponseBody.created(GuestCreateResponse.from(tokenPair))
    }
}
