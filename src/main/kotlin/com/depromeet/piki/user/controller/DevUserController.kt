package com.depromeet.piki.user.controller

import com.depromeet.piki.auth.controller.dto.GuestCreateResponse
import com.depromeet.piki.auth.service.AuthService
import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.common.response.PageResponse
import com.depromeet.piki.user.controller.dto.DevUserSummaryResponse
import com.depromeet.piki.user.repository.UserJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// dev 전용 컨트롤러라 UserJpaRepository 를 직접 주입한다.
// UserRepository 인터페이스에 findAll() 을 추가하면 이 파일을 걷어낼 때
// 인터페이스·impl·service·stub 네 곳을 함께 정리해야 해서 삭제 비용이 커진다.
@Profile("!prod")
@RestController
@RequestMapping("/api/v1/dev/users")
class DevUserController(
    private val userJpaRepository: UserJpaRepository,
    private val authService: AuthService,
) : DevUserApi {
    @GetMapping
    override fun listUsers(
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam cursor: String?,
    ): ApiResponseBody<List<DevUserSummaryResponse>> {
        val page = cursor?.toIntOrNull() ?: 0
        val cappedSize = size.coerceIn(1, 200)
        // cursor 는 페이지 번호를 String 으로 인코딩한다.
        // 실제 커서 기반(created_at / id 필터)이 아니라 오프셋 차용 — dev 편의 구현이라 빠르게 때운 것.
        val result = userJpaRepository.findAll(
            PageRequest.of(page, cappedSize, Sort.by(Sort.Direction.ASC, "createdAt")),
        )
        val pageResponse = PageResponse(
            nextCursor = if (result.hasNext()) (page + 1).toString() else null,
            hasNext = result.hasNext(),
        )
        return ApiResponseBody.ok(result.content.map { DevUserSummaryResponse.from(it) }, pageResponse)
    }

    @GetMapping("/{userId}")
    override fun getUser(
        @PathVariable userId: UUID,
    ): ApiResponseBody<GuestCreateResponse> {
        val result = authService.issueTokenForExistingUser(userId)
        return ApiResponseBody.ok(GuestCreateResponse.from(result.tokenPair, result.user))
    }
}
