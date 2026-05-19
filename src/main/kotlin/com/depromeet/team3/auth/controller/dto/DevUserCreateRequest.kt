package com.depromeet.team3.auth.controller.dto

import com.depromeet.team3.user.domain.IdentityType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "개발용 유저 생성 요청")
data class DevUserCreateRequest(
    @field:NotNull
    @field:Schema(description = "유저 타입", example = "GUEST", requiredMode = Schema.RequiredMode.REQUIRED)
    val identityType: IdentityType,
    @field:Schema(description = "닉네임 (MEMBER 필수, GUEST 는 자동 생성)", example = "홍길동")
    val nickname: String? = null,
)
