package com.depromeet.piki.user.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.user.controller.dto.MyProfileResponse
import com.depromeet.piki.user.controller.dto.NicknameCheckRequest
import com.depromeet.piki.user.controller.dto.NicknameCheckResponse
import com.depromeet.piki.user.controller.dto.UserResponse
import com.depromeet.piki.user.controller.dto.UserUpdateRequest
import com.depromeet.piki.user.service.ProfileUpdateService
import com.depromeet.piki.user.service.UserService
import com.depromeet.piki.user.service.WithdrawalService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
    private val withdrawalService: WithdrawalService,
    private val profileUpdateService: ProfileUpdateService,
) : UserApi {
    @GetMapping("/me")
    override fun getMe(
        @AuthenticationPrincipal userId: UUID,
    ): ApiResponseBody<MyProfileResponse> {
        val profile = userService.getMyProfile(userId)
        return ApiResponseBody.ok(MyProfileResponse.from(profile.user, profile.email))
    }

    @PatchMapping("/me", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun updateMe(
        @AuthenticationPrincipal userId: UUID,
        @Valid @ModelAttribute request: UserUpdateRequest,
    ): ApiResponseBody<UserResponse> {
        // 닉네임·프로필 이미지를 한 요청으로 부분 수정한다 — 들어온 필드만 갱신. 이미지 S3 업로드(외부 호출)는
        // ProfileUpdateService 가 트랜잭션 밖에서, 영속화는 짧은 단일 트랜잭션으로 처리해 부분 성공을 막는다.
        // email 은 수정 대상이 아니므로 수정 응답엔 담지 않는다 (PII 표면 최소화). 마이페이지 email 은 GET /me 가 제공.
        val user = profileUpdateService.updateMe(userId, request.nickname, request.image)
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
        @AuthenticationPrincipal userId: UUID?,
        @Valid request: NicknameCheckRequest,
    ): ApiResponseBody<NicknameCheckResponse> =
        ApiResponseBody.ok(
            NicknameCheckResponse(available = userService.isNicknameAvailable(request.nickname, userId)),
        )
}
