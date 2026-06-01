package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 알림 저장만 짧은 트랜잭션으로 묶는다. Dispatcher 는 @Async(AFTER_COMMIT) 라 트랜잭션 밖에서 도므로,
// 영속화를 별도 빈에 위임해 AOP proxy 를 거치게 한다 (self-invocation 회피, WishPersistenceService 패턴).
@Service
class NotificationPersistenceService(
    private val notificationRepository: NotificationRepository,
) {
    @Transactional
    fun save(notification: Notification): Notification = notificationRepository.save(notification)
}
