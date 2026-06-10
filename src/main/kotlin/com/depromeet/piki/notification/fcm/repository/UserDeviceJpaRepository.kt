package com.depromeet.piki.notification.fcm.repository

import com.depromeet.piki.notification.fcm.domain.UserDevice
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserDeviceJpaRepository : JpaRepository<UserDevice, Long> {
    fun findByFcmToken(fcmToken: String): UserDevice?

    fun findByUserIdAndDeviceId(userId: UUID, deviceId: String): UserDevice?

    fun findAllByUserId(userId: UUID): List<UserDevice>

    fun deleteByUserIdAndDeviceId(userId: UUID, deviceId: String)

    fun deleteAllByFcmTokenIn(fcmTokens: Collection<String>)

    fun deleteAllByUserId(userId: UUID)
}
