package com.depromeet.piki.notification.service

import com.depromeet.piki.common.config.AsyncConfig
import com.depromeet.piki.item.event.ItemParsingCompleted
import com.depromeet.piki.item.event.ItemParsingFailed
import com.depromeet.piki.tournament.event.TournamentItemAdded
import com.depromeet.piki.tournament.event.TournamentItemDeleted
import com.depromeet.piki.tournament.event.TournamentJoined
import com.depromeet.piki.tournament.event.TournamentStarted
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// 도메인 이벤트를 구독해 NotificationDispatcher 로 위임한다.
// 결합 방향: 알림 -> 도메인 (단방향). 도메인은 알림 패키지를 import 하지 않는다.
// 발행 트랜잭션이 커밋된 뒤(AFTER_COMMIT)에만, 별도 스레드(@Async)에서 디스패치한다 — 롤백 시 발송 안 됨.
// 새 알림 이벤트 추가 = 도메인 이벤트 정의 + 알림 핸들러 빈 + 여기에 리스너 메서드 1줄 추가.
@Component
class NotificationEventListener(
    private val dispatcher: NotificationDispatcher,
) {
    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ItemParsingCompleted) {
        dispatcher.dispatch(event)
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ItemParsingFailed) {
        dispatcher.dispatch(event)
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: TournamentItemAdded) {
        dispatcher.dispatch(event)
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: TournamentItemDeleted) {
        dispatcher.dispatch(event)
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: TournamentJoined) {
        dispatcher.dispatch(event)
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: TournamentStarted) {
        dispatcher.dispatch(event)
    }
}
