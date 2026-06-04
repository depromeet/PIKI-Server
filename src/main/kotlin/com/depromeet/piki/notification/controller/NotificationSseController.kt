package com.depromeet.piki.notification.controller

import com.depromeet.piki.notification.sse.SseEmitterRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationSseController(
    private val registry: SseEmitterRegistry,
) : NotificationSseApi {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/subscribe", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    override fun subscribe(
        @AuthenticationPrincipal userId: UUID,
    ): SseEmitter {
        val emitter = SseEmitter(SSE_TIMEOUT_MS)
        // 연결 종료(정상 종료·에러·타임아웃) 시 레지스트리에서 제거해 죽은 emitter 누적을 막는다. unregister 는 멱등.
        emitter.onCompletion { registry.unregister(userId, emitter) }
        emitter.onError { registry.unregister(userId, emitter) }
        emitter.onTimeout {
            registry.unregister(userId, emitter)
            emitter.complete()
        }
        registry.register(userId, emitter)
        // 최초 connect 이벤트로 응답 헤더를 즉시 flush 해 클라이언트가 "연결됨" 을 곧장 인지하게 한다.
        runCatching {
            emitter.send(SseEmitter.event().name(EVENT_CONNECT).data("connected"))
        }.onFailure { e ->
            log.warn("SSE 최초 connect 전송 실패 userId={}", userId, e)
            registry.unregister(userId, emitter)
            emitter.completeWithError(e)
        }
        return emitter
    }

    companion object {
        // connect 이벤트 name. 알림(notification)·하트비트(주석)와 구분된다.
        const val EVENT_CONNECT = "connect"

        // emitter 자체 타임아웃(30분). 만료되면 onTimeout 으로 정리되고 클라이언트가 재연결한다.
        const val SSE_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
