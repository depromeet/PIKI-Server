package com.depromeet.piki.admin.controller.dto

import jakarta.validation.constraints.NotBlank

data class AdminChatRequest(
    @field:NotBlank(message = "메시지를 입력해 주세요.")
    val message: String,
)
