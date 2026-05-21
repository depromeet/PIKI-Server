package com.depromeet.team3.user.controller.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "닉네임 중복 체크 요청 (query parameter)")
data class NicknameCheckRequest(
    @field:NotBlank(message = "nickname 은 비어 있거나 공백만으로 구성될 수 없다.")
    @field:Size(max = 10, message = "nickname 은 10자 이하여야 한다.")
    @field:Schema(description = "확인할 닉네임 (최대 10자)", example = "새닉네임")
    val nickname: String,
)
