package com.depromeet.piki.notification.sse

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// 주기적으로 모든 SSE 연결에 ping 을 흘려 연결을 살아있게 유지하고, 끊긴 연결을 정리한다(write 는 LocalSseDelivery).
//
// 운영 nginx 의 proxy_read_timeout(60s)보다 짧은 주기여야 프록시가 idle SSE 연결을 끊지 않는다.
// 다만 진짜 제약은 nginx 한 구간이 아니라 경로상 가장 빡빡한 idle timeout 이다 — 모바일 캐리어 NAT 등
// 클라이언트 쪽 중간 장비까지 고려해, 업계 SSE 하트비트의 표준 범위(25~30초)인 30s 로 둔다. nginx 60s
// 대비 절반이라 keep-alive 여유가 있고, 더 늘리면(45s+) 단일 스케줄러 스레드 지연 시 60s 에 근접할 수
// 있어 30s 에서 멈춘다. (ping 은 수 바이트라 주기를 줄여도 대역폭 이득은 미미하다.)
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
        // nginx proxy_read_timeout(60s) 아래로 두되, 모바일 NAT idle timeout 까지 고려한 30s.
        const val HEARTBEAT_INTERVAL_MS = 30_000L
    }
}
