package com.depromeet.piki.notification.fcm.repository

import com.depromeet.piki.notification.fcm.domain.UserDevice
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class UserDeviceRepositoryImpl(
    private val userDeviceJpaRepository: UserDeviceJpaRepository,
) : UserDeviceRepository {
    override fun save(userDevice: UserDevice): UserDevice = userDeviceJpaRepository.save(userDevice)

    override fun findByFcmToken(fcmToken: String): UserDevice? = userDeviceJpaRepository.findByFcmToken(fcmToken)

    override fun findByUserIdAndDeviceId(
        userId: UUID,
        deviceId: String,
    ): UserDevice? = userDeviceJpaRepository.findByUserIdAndDeviceId(userId, deviceId)

    override fun findAllByUserId(userId: UUID): List<UserDevice> = userDeviceJpaRepository.findAllByUserId(userId)

    override fun deleteByUserIdAndDeviceId(
        userId: UUID,
        deviceId: String,
    ) = userDeviceJpaRepository.deleteByUserIdAndDeviceId(userId, deviceId)

    override fun deleteAllByFcmTokenIn(fcmTokens: Collection<String>) = userDeviceJpaRepository.deleteAllByFcmTokenIn(fcmTokens)

    override fun delete(userDevice: UserDevice) = userDeviceJpaRepository.delete(userDevice)

    override fun deleteAllByUserId(userId: UUID) = userDeviceJpaRepository.deleteAllByUserId(userId)

    override fun flush() = userDeviceJpaRepository.flush()
}
