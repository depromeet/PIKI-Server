package com.depromeet.piki.common.monitoring

import com.depromeet.piki.common.logging.LoggingKeys
import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.protocol.Request
import io.sentry.protocol.User
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping

// Sentry 이벤트에 우리 요청 컨텍스트를 명시 부착한다. send-default-pii=false 라 헤더·쿠키·바디는 안 들어오고,
// 여기서 안전한 값만 골라 싣는다 — Sentry 이슈에서 traceId 로 Grafana Tempo 트레이스로 피벗(핵심 연결고리),
// userId 로 영향 유저 식별, adminActor 로 /admin 행위자 구분, endpoint(라우트 템플릿)·provider(소셜)로 슬라이스.
// URL 쿼리스트링은 토큰이 실릴 수 있어 떼어낸다.
//
// 캡처는 로그 이벤트(GlobalExceptionHandler 의 log.error/warn) 시점에 그 요청 스레드에서 일어나므로, MDC 의
// traceId/userId/adminActor 가 채워져 있다. traceId 는 Tracer 대신 MDC 키로 읽어(로깅이 이미 채운 값) Tracer
// 의존을 없애고 순수 변환으로 단위 테스트한다.
@Component
class SentryContextEventProcessor : EventProcessor {
    override fun process(
        event: SentryEvent,
        hint: Hint,
    ): SentryEvent {
        currentTraceId()?.let { event.setTag(TRACE_ID_TAG, it) }
        MDC.get(LoggingKeys.USER_ID)?.let { userId ->
            event.user = (event.user ?: User()).apply { id = userId }
        }
        MDC.get(LoggingKeys.ADMIN_ACTOR)?.let { event.setTag(ADMIN_ACTOR_TAG, it) }
        currentRequest()?.let { request ->
            event.setTag(ENDPOINT_TAG, endpointOf(request))
            providerOf(request)?.let { event.setTag(PROVIDER_TAG, it) }
        }
        event.request?.let(::scrubQuery)
        return event
    }

    private fun currentRequest(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    // 라우트 템플릿(예: POST /api/v1/wishlists/{id})으로 묶어 구체 경로(/1·/2)가 다른 이슈로 흩어지지 않게 한다.
    // 핸들러 매칭 전(404 등)이면 best pattern 이 없어 requestURI 로 폴백한다.
    private fun endpointOf(request: HttpServletRequest): String {
        val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
        return "${request.method} ${pattern ?: request.requestURI}"
    }

    // {provider} path 변수가 있으면(소셜 로그인 등) 그 값을 태그로 — "어느 provider(kakao/google/apple)가 실패 많나" 슬라이스.
    // URL 구조를 하드코딩하지 않고 path 변수 이름으로 generic 하게 잡는다.
    @Suppress("UNCHECKED_CAST")
    private fun providerOf(request: HttpServletRequest): String? {
        val vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String>
        return vars?.get(PROVIDER_TAG)
    }

    // noop tracer 면 traceId 가 전부 '0' 으로 와 유효하지 않다(Tempo 검색 불가) → 태그로 치지 않는다.
    // (TraceIdHeaderFilter 의 폴백과 동일한 판정.)
    private fun currentTraceId(): String? = MDC.get(MDC_TRACE_ID)?.takeIf { id -> id.any { it != '0' } }

    // URL 쿼리스트링에 인증 토큰이 실릴 수 있어(CLAUDE.md 민감정보 마스킹) path 만 남기고 떼어낸다.
    private fun scrubQuery(request: Request) {
        request.queryString = null
        request.url = request.url?.substringBefore('?')
    }

    companion object {
        private const val MDC_TRACE_ID = "traceId"
        const val TRACE_ID_TAG = "trace_id"
        const val ADMIN_ACTOR_TAG = "admin_actor"
        const val ENDPOINT_TAG = "endpoint"
        const val PROVIDER_TAG = "provider"
    }
}
