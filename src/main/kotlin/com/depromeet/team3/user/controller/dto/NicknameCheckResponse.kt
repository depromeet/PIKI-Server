package com.depromeet.team3.user.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "닉네임 중복 체크 결과")
data class NicknameCheckResponse(
    @field:Schema(description = "사용 가능 여부 (이미 다른 유저가 쓰고 있으면 false)")
    val available: Boolean,
)
