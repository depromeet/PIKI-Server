package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.notification.fcm.domain.UserDevice
import com.depromeet.piki.notification.fcm.repository.UserDeviceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// FCM 기기 토큰 등록/해제(#244). 발송(#245)은 PushNotificationChannel 이 repository 를 직접 사용한다.
@Service
class UserDeviceService(
    private val userDeviceRepository: UserDeviceRepository,
) {
    // 토큰 등록/갱신(POST). upsert 다 — 앱 진입·토큰 회전 어느 경로로 와도 기기당 1 row 로 수렴한다.
    // 배달 정확성을 위해 같은 토큰을 들고 있던 다른 사용자 row 는 먼저 해제하고, 내 (user,device) row 가
    // 있으면 토큰만 교체, 없으면 신규 생성한다.
    @Transactional
    fun register(
        userId: UUID,
        deviceId: String,
        fcmToken: String,
    ): UserDevice {
        val mine = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
        releaseStaleTokenHolder(fcmToken, keep = mine)
        mine ?: return userDeviceRepository.save(
            UserDevice(userId = userId, deviceId = deviceId, fcmToken = fcmToken),
        )
        mine.refreshToken(fcmToken)
        return userDeviceRepository.save(mine)
    }

    // 기기 해제(DELETE, 로그아웃). 멱등 — 이미 없는 기기를 지워도 무해하다.
    @Transactional
    fun unregister(
        userId: UUID,
        deviceId: String,
    ) {
        userDeviceRepository.deleteByUserIdAndDeviceId(userId, deviceId)
    }

    // 같은 토큰을 들고 있던 다른 row 를 해제한다 — 토큰당 1 row(배달 정확성)를 유지하기 위함.
    // keep(내 기기 row)과 동일 row 면 두고, 다르면 지운 뒤 flush 해 UNIQUE(fcm_token) 충돌을 막는다.
    private fun releaseStaleTokenHolder(
        fcmToken: String,
        keep: UserDevice?,
    ) {
        val holder = userDeviceRepository.findByFcmToken(fcmToken) ?: return
        keep?.let { if (holder.getId() == it.getId()) return }
        userDeviceRepository.delete(holder)
        userDeviceRepository.flush()
    }
}
