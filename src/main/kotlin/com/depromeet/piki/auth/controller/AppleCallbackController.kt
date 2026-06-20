package com.depromeet.piki.auth.controller

import com.depromeet.piki.auth.infrastructure.oauth.apple.AppleProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder

// Apple 웹 OAuth 콜백 브릿지(#430). Apple 은 scope=email name 이면 response_mode=form_post 를 강제해
// 콜백을 POST(form_post)로 보낸다. Kakao·Google 은 GET 쿼리라 프론트 공용 콜백이 그대로 처리하지만 Apple 만
// POST 라 못 받는다. 이 엔드포인트가 Apple 의 POST 를 받아 code·state 를 프론트 공용 콜백으로 303(GET 쿼리)
// 시켜, Apple 도 Kakao·Google 과 동일한 흐름을 타게 한다. POST→GET 전환이라 302(메서드 유지 재량)가 아니라
// 303 See Other(메서드를 GET 으로 강제)가 의도에 정확하다 (RFC 7231 §6.4.4).
//
// 로그인은 하지 않는다 — 토큰교환·로그인·쿠키 발급은 리다이렉트 이후 프론트 공용 핸들러가 기존
// /api/v1/auth/login/apple 을 호출해 수행한다(state 검증도 그쪽). 따라서 여기선 state 를 소비하지 않고 그대로 넘긴다.
@RestController
@RequestMapping("/api/v1/auth/apple")
class AppleCallbackController(
    private val appleProperties: AppleProperties,
) : AppleCallbackApi {
    @PostMapping("/callback", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    override fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> {
        // open-redirect 방지: 목적지 host·path 는 설정값(webCallbackUrl)으로 고정하고, Apple 이 보낸 값은
        // 쿼리 파라미터로만 덧붙인다. 값은 .encode() 가 인코딩하므로 host 를 바꿔치기할 수 없다.
        val location =
            UriComponentsBuilder
                .fromUriString(appleProperties.webCallbackUrl)
                .apply {
                    // Apple 이 실패(취소 등)면 code 없이 error 가 온다. 프론트 공용 핸들러가 일관 처리하도록 그대로 전달.
                    error?.let { queryParam(PARAM_ERROR, it) }
                    code?.let { queryParam(PARAM_CODE, it) }
                    state?.let { queryParam(PARAM_STATE, it) }
                }.build()
                .encode()
                .toUri()
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build()
    }

    companion object {
        private const val PARAM_CODE = "code"
        private const val PARAM_STATE = "state"
        private const val PARAM_ERROR = "error"
    }
}
