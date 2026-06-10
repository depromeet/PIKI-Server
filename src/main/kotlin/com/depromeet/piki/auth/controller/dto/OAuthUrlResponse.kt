package com.depromeet.piki.auth.controller.dto

import com.depromeet.piki.auth.service.dto.OAuthUrlResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "OAuth 인가 URL 응답")
data class OAuthUrlResponse(
    @field:Schema(description = "사용자를 redirect 시킬 provider 인가 URL (state 포함)")
    val url: String,
    @field:Schema(description = "CSRF 방지용 state 값. 로그인 완료 시 POST /auth/login/{provider} 에 함께 전송")
    val state: String,
) {
    companion object {
        fun from(result: OAuthUrlResult) = OAuthUrlResponse(url = result.url, state = result.state)
    }
}
