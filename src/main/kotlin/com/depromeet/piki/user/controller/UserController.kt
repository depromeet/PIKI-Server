package com.depromeet.piki.user.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.user.controller.dto.NicknameCheckRequest
import com.depromeet.piki.user.controller.dto.NicknameCheckResponse
import com.depromeet.piki.user.controller.dto.UserResponse
import com.depromeet.piki.user.controller.dto.UserUpdateRequest
import com.depromeet.piki.user.service.UserService
import com.depromeet.piki.user.service.WithdrawalService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
    private val withdrawalService: WithdrawalService,
) : UserApi {
    @GetMapping("/me")
    override fun getMe(
        @AuthenticationPrincipal userId: UUID,
    ): ApiResponseBody<UserResponse> {
        val user = userService.findById(userId)
        return ApiResponseBody.ok(UserResponse.from(user))
    }

    @PatchMapping("/me")
    override fun updateMe(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: UserUpdateRequest,
    ): ApiResponseBody<UserResponse> {
        val user =
            request.nickname?.let { userService.updateNickname(userId, it) }
                ?: userService.findById(userId)
        return ApiResponseBody.ok(UserResponse.from(user))
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    override fun withdraw(
        @AuthenticationPrincipal userId: UUID,
    ): ApiResponseBody<Unit> {
        withdrawalService.withdraw(userId)
        return ApiResponseBody.ok()
    }

    @GetMapping("/nickname/check")
    override fun checkNickname(
        @AuthenticationPrincipal userId: UUID,
        @Valid request: NicknameCheckRequest,
    ): ApiResponseBody<NicknameCheckResponse> =
        ApiResponseBody.ok(
            NicknameCheckResponse(available = userService.isNicknameAvailable(request.nickname, userId)),
        )
}
