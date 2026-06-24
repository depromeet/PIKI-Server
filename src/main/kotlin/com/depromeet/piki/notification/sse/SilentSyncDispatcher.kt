package com.depromeet.piki.notification.sse

import com.depromeet.piki.common.config.AsyncConfig
import com.depromeet.piki.notification.controller.dto.SilentSyncPayload
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID

// 응답 경로(NotificationReadOrchestrator.readAndSyncBadge)에서 silent-sync 발행을 @Async 로 떼어내는 얇은 진입점.
// LocalSseDelivery.deliverSilentSync 는 동기 로컬 write 라, 읽음 요청 스레드에서 직접 부르면 (1) 느린 SSE 소켓의
// emitter.send() 블로킹이 읽음 응답 latency 를 좌우하고, (2) 거기서 throw 하면 이미 커밋된 읽음이 500 이 되며 뒤따르는
// FCM syncBadge 까지 건너뛴다. 이 빈을 거치면 그 셋이 응답 경로 밖(notificationExecutor)으로 빠진다.
//
// broadcaster 경로는 이미 @Async 워커에서 LocalSseDelivery 를 직접 호출하므로 이 진입점이 필요 없다 — 응답 경로 전용이다.
// 별도 빈으로 둔 건 self-invocation 회피다(같은 빈 안에서 @Async 메서드를 직접 부르면 proxy 를 안 거쳐 동기로 돈다).
@Component
class SilentSyncDispatcher(
    private val localDelivery: LocalSseDelivery,
) {
    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    fun dispatch(
        userIds: Collection<UUID>,
        payload: SilentSyncPayload,
    ) {
        localDelivery.deliverSilentSync(userIds, payload)
    }
}
