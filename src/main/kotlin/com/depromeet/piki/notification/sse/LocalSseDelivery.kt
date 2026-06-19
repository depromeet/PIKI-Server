package com.depromeet.piki.notification.sse

import com.depromeet.piki.notification.controller.dto.NotificationSsePayload
import com.depromeet.piki.notification.controller.dto.TournamentItemParsedPayload
import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.service.DefaultPushImage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

// SSE 의 "로컬 write" 지점 — 레지스트리에 든 emitter 들에 실제로 이벤트를 흘려보내고, write 가 실패하는
// 죽은 연결을 정리한다. emitter write 가 일어나는 곳은 여기 한 곳으로 모은다(전달·하트비트 공용).
//
// 외부 진입(SseNotificationChannel.send)과 분리한 이유는 스케일아웃 seam 이다: 다중
// 인스턴스가 되면 send() 는 Redis publish 로 바뀌고, 각 인스턴스의 Redis subscriber 가 이 deliver() 를
// 호출(= 모든 인스턴스가 자기 로컬 연결에만 write)한다. 그 전환에서 이 클래스와 레지스트리는 그대로 산다.
@Component
class LocalSseDelivery(
    private val registry: SseEmitterRegistry,
    private val defaultPushImage: DefaultPushImage,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 한 유저의 모든 연결에 알림 이벤트를 보낸다. 이벤트는 유저당 한 번 만들어 그 유저의 emitter 들에 재사용한다.
    fun deliver(
        userId: UUID,
        notification: Notification,
    ) {
        val event =
            SseEmitter
                .event()
                .name(EVENT_NOTIFICATION)
                .data(NotificationSsePayload.from(notification, defaultPushImage.url))
        registry.emittersOf(userId).forEach { sendOrEvict(userId, it, event) }
    }

    // 토너먼트 출전 아이템의 파싱 완료/실패를 그 토너먼트 참여자들 연결에 실시간 흘려보낸다(라이브 동기화, 알림 아님).
    // notification 전달과 같은 emitter write 경로(sendOrEvict)를 공유하되 이벤트 name 만 다르다(클라가 name 으로 구분).
    // 한 payload 이벤트를 만들어 대상 유저 전원의 emitter 에 재사용한다 — 같은 토너먼트 참여자들이 같은 카드 갱신을 받는다.
    fun deliverTournamentItemParsed(
        userIds: Collection<UUID>,
        payload: TournamentItemParsedPayload,
    ) {
        val event =
            SseEmitter
                .event()
                .name(EVENT_TOURNAMENT_ITEM_PARSED)
                .data(payload)
        userIds.forEach { userId -> registry.emittersOf(userId).forEach { sendOrEvict(userId, it, event) } }
    }

    // 탈퇴 시 그 유저의 모든 SSE 연결을 즉시 끊는다(best-effort). 레지스트리에서 키째 빼고 각 emitter 를
    // complete 한다. complete 가 컨트롤러의 onCompletion(unregister)을 다시 깨워도 unregister 는 멱등이라 무해.
    // 인스턴스-로컬 연결만 끊는다 — 멀티 인스턴스로 확장돼도 각 인스턴스가 자기 메모리 연결만 정리하면 된다.
    fun closeAll(userId: UUID) {
        registry.removeAll(userId).forEach { emitter ->
            runCatching { emitter.complete() }
                .onFailure { e -> log.warn("SSE 연결 종료 실패 userId={}", userId, e) }
        }
    }

    // 전 연결에 주석 ping 을 보내 연결을 살아있게 유지하고, 끊긴 연결을 정리한다. (스케줄러가 주기 호출)
    fun heartbeat() {
        val ping = SseEmitter.event().comment("ping")
        registry.forEach { userId, emitter -> sendOrEvict(userId, emitter, ping) }
    }

    // write 실패 = 죽은 연결(클라이언트가 끊겼는데 onError/onCompletion 콜백이 아직 안 탄 경우 등).
    // 레지스트리에서 빼 더 흘려보내지 않게 하고 completeWithError 로 정리를 마무리한다. completeWithError 가
    // onError 콜백(컨트롤러의 unregister)을 다시 깨울 수 있으나 unregister 는 멱등이라 중복 호출이 무해하다.
    private fun sendOrEvict(
        userId: UUID,
        emitter: SseEmitter,
        event: SseEmitter.SseEventBuilder,
    ) {
        runCatching { emitter.send(event) }
            .onFailure { e ->
                log.warn("SSE write 실패로 emitter 정리 userId={}", userId, e)
                registry.unregister(userId, emitter)
                runCatching { emitter.completeWithError(e) }
            }
    }

    companion object {
        // SSE 이벤트 name. 클라이언트는 이 이름으로 알림 이벤트와 connect/하트비트를 구분한다.
        const val EVENT_NOTIFICATION = "notification"

        // 토너먼트 출전 아이템 파싱 완료/실패 화면 갱신 신호의 SSE 이벤트 name. notification 과 구분되며,
        // 알림이 아니라 라이브 동기화라 알림센터·FCM 을 거치지 않는다(TournamentItemParsedPayload).
        const val EVENT_TOURNAMENT_ITEM_PARSED = "tournament-item-parsed"
    }
}
