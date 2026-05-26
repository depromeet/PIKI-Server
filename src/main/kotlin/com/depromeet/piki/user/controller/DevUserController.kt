package com.depromeet.piki.user.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.user.controller.dto.DevUserSummaryResponse
import com.depromeet.piki.user.controller.dto.UserResponse
import com.depromeet.piki.user.service.UserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/dev/users")
class DevUserController(
    private val userService: UserService,
) : DevUserApi {
    @GetMapping
    override fun listUsers(): ApiResponseBody<List<DevUserSummaryResponse>> =
        ApiResponseBody.ok(userService.findAll().map { DevUserSummaryResponse.from(it) })

    @GetMapping("/{userId}")
    override fun getUser(
        @PathVariable userId: UUID,
    ): ApiResponseBody<UserResponse> =
        ApiResponseBody.ok(UserResponse.from(userService.findById(userId)))
}
