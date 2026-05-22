package com.depromeet.team3.auth.controller

import com.depromeet.team3.auth.controller.dto.DevUserCreateRequest
import com.depromeet.team3.auth.controller.dto.GuestCreateResponse
import com.depromeet.team3.auth.service.AuthService
import com.depromeet.team3.common.response.ApiResponseBody
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/dev")
class DevAuthController(
    private val authService: AuthService,
) : DevAuthApi {
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    override fun createDevUser(
        @Valid @RequestBody request: DevUserCreateRequest,
    ): ApiResponseBody<GuestCreateResponse> {
        val result = authService.createMember(request.nickname)
        return ApiResponseBody.created(GuestCreateResponse.from(result.tokenPair, result.user))
    }
}
