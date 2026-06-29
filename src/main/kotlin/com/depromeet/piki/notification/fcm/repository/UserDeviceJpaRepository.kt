package com.depromeet.piki.notification.fcm.repository

import com.depromeet.piki.notification.fcm.domain.UserDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserDeviceJpaRepository : JpaRepository<UserDevice, Long> {
    fun findByFcmToken(fcmToken: String): UserDevice?

    // 토큰 보유자(= 앱 푸시 수신 가능 유저) 수 — 한 유저가 여러 기기를 가질 수 있어 DISTINCT user_id 로 센다.
    // 공지 발송 대상 인원 표시(백오피스)·#489 fan-out 규모 산정에 쓰인다.
    @Query("SELECT COUNT(DISTINCT d.userId) FROM UserDevice d")
    fun countDistinctUsers(): Long

    // 공지 fan-out(#489) — 토큰 보유 유저 전체의 식별자. 한 유저가 여러 기기를 가질 수 있어 DISTINCT.
    // 브로드캐스터가 이 id 들을 돌며 유저별 알림 생성 + 멀티캐스트한다.
    @Query("SELECT DISTINCT d.userId FROM UserDevice d")
    fun findDistinctUserIds(): List<UUID>

    fun findByUserIdAndDeviceId(userId: UUID, deviceId: String): UserDevice?

    fun findAllByUserId(userId: UUID): List<UserDevice>

    fun deleteByUserIdAndDeviceId(userId: UUID, deviceId: String)

    fun deleteAllByFcmTokenIn(fcmTokens: Collection<String>)

    fun deleteAllByUserId(userId: UUID)

    // 토큰 보유 row 멱등 삭제(#244 reconcile, #396 후속) — holder 를 PK 로 직접 삭제한다.
    // 엔티티 delete(정확히 1 row 단언) 대신 bulk delete 라, 동시 등록으로 다른 트랜잭션이 같은 holder 를 먼저 지워
    // 0 row 가 돼도 StaleStateException(ObjectOptimisticLockingFailureException → 500) 없이 무해하다.
    // 삭제 조건을 PK 로 두는 게 핵심 — fcm_token(UNIQUE 인덱스) 기준으로 지우면 갭락을 잡아 같은 토큰 동시 INSERT 와
    // 데드락이 나므로, 엔티티 delete 와 동일한 PK 락 프로파일을 유지해 데드락 없이 행 수 단언만 떼어낸다.
    @Modifying
    @Query("DELETE FROM UserDevice d WHERE d.id = :id")
    fun deleteByIdBulk(
        @Param("id") id: Long,
    ): Int
}
