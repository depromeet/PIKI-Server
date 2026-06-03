package com.depromeet.piki.auth.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "개발용 MEMBER 생성 요청")
data class DevUserCreateRequest(
    @field:NotBlank(message = "닉네임은 필수입니다.")
    @field:Schema(description = "닉네임", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    val nickname: String,
)
