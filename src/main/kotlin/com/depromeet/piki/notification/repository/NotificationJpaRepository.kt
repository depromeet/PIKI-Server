package com.depromeet.piki.notification.repository

import com.depromeet.piki.notification.domain.Notification
import org.springframework.data.domain.Limit
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

    // 히스토리 목록 첫 페이지 — 본인 알림을 최신순(id desc)으로 limit 건. (idx_notifications_user_id_is_read 의 user_id leftmost)
    fun findByUserIdOrderByIdDesc(
        userId: UUID,
        limit: Limit,
    ): List<Notification>

    // 히스토리 목록 다음 페이지 — 커서(직전 페이지 마지막 id) 미만만 최신순 limit 건.
    fun findByUserIdAndIdLessThanOrderByIdDesc(
        userId: UUID,
        id: Long,
        limit: Limit,
    ): List<Notification>

    // 안읽음 수(badge). (idx_notifications_user_id_is_read 가 그대로 커버)
    fun countByUserIdAndIsReadFalse(userId: UUID): Long

    // 본인 소유(user_id) + 지정 id + 아직 안읽음만 read 로. user_id 가 WHERE 에 있어 타인/없는 id 는 무영향(소유 검증 겸용).
    // 영향 건수를 돌려준다. 멱등(이미 읽음·없는 id 는 0건).
    // 벌크 UPDATE 는 1차 캐시를 우회하므로, 같은 트랜잭션에서 이후 재조회가 stale 값을 읽지 않도록 컨텍스트를 flush·clear 한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.id IN :ids AND n.isRead = false")
    fun markReadByUserIdAndIds(
        @Param("userId") userId: UUID,
        @Param("ids") ids: Collection<Long>,
    ): Int

    // 본인 안읽음 전부 read. 영향 건수를 돌려준다. 멱등.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    fun markAllReadByUserId(
        @Param("userId") userId: UUID,
    ): Int
}
