package com.depromeet.piki.notification.repository

import com.depromeet.piki.notification.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.UUID

interface NotificationJpaRepository : JpaRepository<Notification, Long> {
    // 탈퇴 cascade — 그 수신자(userId) 의 활성 알림을 일괄 soft-delete. 이미 삭제된 행은 건드리지 않아 멱등.
    // Notification 은 deletedAt(BaseEntity)·userId(수신자) 를 가져 wishes 와 같은 soft-delete 패턴을 따른다.
    @Modifying
    @Query("UPDATE Notification n SET n.deletedAt = :now WHERE n.userId = :userId AND n.deletedAt IS NULL")
    fun softDeleteAllByUserId(
        @Param("userId") userId: UUID,
        @Param("now") now: LocalDateTime,
    ): Int

    // 30일 파기 — soft-delete 된 알림을 영구 하드삭제.
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    fun hardDeleteAllByUserId(
        @Param("userId") userId: UUID,
    ): Int
}
