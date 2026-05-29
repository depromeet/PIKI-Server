package com.depromeet.piki.admin.controller.dto

import jakarta.validation.constraints.NotBlank

data class AdminConfirmRequest(
    @field:NotBlank
    val actionId: String,
)
