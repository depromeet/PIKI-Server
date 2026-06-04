package com.depromeet.piki.notification.sse

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// 주기적으로 모든 SSE 연결에 ping 을 흘려 연결을 살아있게 유지하고, 끊긴 연결을 정리한다(write 는 LocalSseDelivery).
//
// 운영 nginx 의 proxy_read_timeout(90s)보다 짧은 주기여야 프록시가 idle SSE 연결을 끊지 않는다 —
// HEARTBEAT_INTERVAL_MS=15s 로 충분한 여유를 둔다.
//
// 단일 인스턴스 기준이지만, 멀티 인스턴스로 확장돼도 각 인스턴스가 "자기 메모리의 연결만" ping 하므로 중복
// 실행 방지(ShedLock 등)가 필요 없다 — SseEmitter 는 자기 소켓에 묶인 인스턴스-로컬 객체라, @Scheduled 가
// 인스턴스마다 독립적으로 도는 게 오히려 맞다. (StaleProcessingItemSweeper 의 단일 인스턴스 @Scheduled 와 동일 결.)
@Component
class SseHeartbeatScheduler(
    private val localDelivery: LocalSseDelivery,
) {
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    fun ping() {
        localDelivery.heartbeat()
    }

    companion object {
        // nginx proxy_read_timeout(90s) 아래로 둔다.
        const val HEARTBEAT_INTERVAL_MS = 15_000L
    }
}
