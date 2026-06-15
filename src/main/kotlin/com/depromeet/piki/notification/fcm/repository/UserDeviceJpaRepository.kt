package com.depromeet.piki.notification.fcm.repository

import com.depromeet.piki.notification.fcm.domain.UserDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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
}
