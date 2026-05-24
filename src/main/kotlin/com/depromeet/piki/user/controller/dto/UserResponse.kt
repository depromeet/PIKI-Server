package com.depromeet.piki.user.controller.dto

import com.depromeet.piki.user.domain.IdentityType
import com.depromeet.piki.user.domain.User
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "유저 정보")
data class UserResponse(
    @field:Schema(description = "유저 식별자", format = "uuid")
    val id: UUID,
    @field:Schema(description = "닉네임 (최대 10자)", example = "뛰어다니는 강아지")
    val nickname: String,
    @field:Schema(description = "프로필 이미지 URL")
    val profileImage: String,
    @field:Schema(description = "유저 종류")
    val identityType: IdentityType,
) {
    companion object {
        fun from(user: User): UserResponse =
            UserResponse(
                id = user.id,
                nickname = user.nickname,
                profileImage = user.profileImage,
                identityType = user.identityType,
            )
    }
}
