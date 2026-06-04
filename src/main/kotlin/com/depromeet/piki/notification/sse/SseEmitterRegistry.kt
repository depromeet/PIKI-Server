package com.depromeet.piki.notification.sse

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// userId -> 그 유저의 활성 SSE 연결들. 한 유저가 여러 탭/기기로 접속하면 emitter 가 여럿이므로 리스트로 든다.
//
// SseEmitter 는 특정 JVM 의 응답 소켓에 묶인 객체라 Redis 로 옮길 수 없다. 다중 인스턴스로
// 확장돼도 연결을 받은 인스턴스가 그 emitter 를 자기 메모리에 드는 건 불가피하며, 인스턴스 간 fan-out 은
// 이 레지스트리를 대체하는 게 아니라 그 위에 Redis Pub/Sub 버스를 얹는 형태가 된다. 그래서 이 클래스는
// 단일/다중 인스턴스 양쪽에서 그대로 살아남는다.
//
// 순회(전달·하트비트) 중 다른 스레드가 연결을 추가/제거할 수 있어, 값 컨테이너로 CopyOnWriteArrayList 를
// 써 순회 스냅샷 안전성을 확보한다(연결 수가 적어 복사 비용은 무시 가능).
@Component
class SseEmitterRegistry {
    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()

    fun register(
        userId: UUID,
        emitter: SseEmitter,
    ) {
        emitters.computeIfAbsent(userId) { CopyOnWriteArrayList() }.add(emitter)
    }

    // 연결 1개를 제거하고, 그 유저의 마지막 연결이었으면 키까지 비워 맵이 죽은 유저로 부풀지 않게 한다.
    // compute 로 "제거 + 빈 리스트면 키 삭제" 를 원자적으로 처리해 register 와의 경합을 막는다. 멱등하다.
    fun unregister(
        userId: UUID,
        emitter: SseEmitter,
    ) {
        emitters.compute(userId) { _, list ->
            list?.apply { remove(emitter) }?.takeIf { it.isNotEmpty() }
        }
    }

    fun emittersOf(userId: UUID): List<SseEmitter> = emitters[userId].orEmpty()

    // 하트비트가 전 연결을 순회한다. (userId, emitter) 쌍으로 평탄화해 넘긴다.
    fun forEach(action: (UUID, SseEmitter) -> Unit) {
        emitters.forEach { (userId, list) -> list.forEach { action(userId, it) } }
    }
}
