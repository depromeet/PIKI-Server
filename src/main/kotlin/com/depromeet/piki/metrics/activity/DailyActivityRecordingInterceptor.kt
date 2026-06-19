package com.depromeet.piki.metrics.activity

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.util.UUID

// 인증된 요청의 userId 를 일별 활동으로 기록한다. Security 필터 뒤(SecurityContext 채워진 후)에 도는 인터셉터라
// JwtAuthenticationFilter 가 심은 principal(UUID)을 그대로 읽는다 — 미인증·슬랙admin 요청은 principal 이 UUID 가
// 아니라 자연히 제외된다. afterCompletion 에서 호출해 응답 생성 경로(critical path) 밖에서 best-effort 로 기록한다.
@Component
class DailyActivityRecordingInterceptor(
    private val recorder: DailyActivityRecorder,
) : HandlerInterceptor {
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is UUID) recorder.record(principal)
    }
}
