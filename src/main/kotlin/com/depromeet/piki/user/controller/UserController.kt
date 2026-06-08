package com.depromeet.piki.user.controller

import com.depromeet.piki.common.response.ApiResponseBody
import com.depromeet.piki.user.controller.dto.MyProfileResponse
import com.depromeet.piki.user.controller.dto.NicknameCheckRequest
import com.depromeet.piki.user.controller.dto.NicknameCheckResponse
import com.depromeet.piki.user.controller.dto.UserResponse
import com.depromeet.piki.user.controller.dto.UserUpdateRequest
import com.depromeet.piki.user.domain.UserException
import com.depromeet.piki.user.service.ProfileImageService
import com.depromeet.piki.user.service.UserService
import com.depromeet.piki.user.service.WithdrawalService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
    private val withdrawalService: WithdrawalService,
    private val profileImageService: ProfileImageService,
) : UserApi {
    @GetMapping("/me")
    override fun getMe(
        @AuthenticationPrincipal userId: UUID,
    ): ApiResponseBody<MyProfileResponse> {
        val profile = userService.getMyProfile(userId)
        return ApiResponseBody.ok(MyProfileResponse.from(profile.user, profile.email))
    }

    @PatchMapping("/me")
    override fun updateMe(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: UserUpdateRequest,
    ): ApiResponseBody<UserResponse> {
        // email 은 수정 대상이 아니므로 수정 응답엔 담지 않는다 (PII 표면 최소화). 마이페이지 email 은 GET /me 가 제공.
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

    @PostMapping("/me/profile-image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    override fun updateProfileImage(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam("image", required = false) image: MultipartFile?,
    ): ApiResponseBody<UserResponse> {
        // image 파트 미첨부는 Spring 이 진입 전 끊어 캐치올(500)로 가므로, required=false 로 받아
        // 도메인 검증(UserException.emptyProfileImage, 400)에 닿게 한다.
        val file = image ?: throw UserException.emptyProfileImage()
        // email 은 수정 대상이 아니므로 수정 응답엔 담지 않는다 (PII 표면 최소화). 마이페이지 email 은 GET /me 가 제공.
        val user = profileImageService.updateProfileImage(userId, file.bytes, file.contentType)
        return ApiResponseBody.ok(UserResponse.from(user))
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
