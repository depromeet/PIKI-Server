package com.depromeet.piki.auth.controller.dto

import com.depromeet.piki.auth.web.TokenClearing
import io.swagger.v3.oas.annotations.media.Schema

// TokenClearing — TokenCookieResponseAdvice 가 (WEB 이면) 토큰 쿠키를 만료시킨다.
// loggedOut 필드는 빈 객체 직렬화(FAIL_ON_EMPTY_BEANS) 회피 겸 완료 확인용.
@Schema(description = "로그아웃 응답")
data class LogoutResponse(
    @field:Schema(description = "로그아웃 완료 여부", example = "true")
    val loggedOut: Boolean = true,
) : TokenClearing
