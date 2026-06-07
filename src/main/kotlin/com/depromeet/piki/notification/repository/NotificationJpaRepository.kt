package com.depromeet.piki.notification.repository

import com.depromeet.piki.notification.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface NotificationJpaRepository : JpaRepository<Notification, Long> {
    // 탈퇴 cascade — 그 수신자(userId) 의 알림을 일괄 하드삭제. 멱등(없으면 0건).
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    fun hardDeleteAllByUserId(
        @Param("userId") userId: UUID,
    ): Int
}
