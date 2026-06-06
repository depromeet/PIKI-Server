package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.notification.fcm.domain.UserDevice
import com.depromeet.piki.notification.fcm.repository.UserDeviceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// FCM 기기 토큰 등록/해제(#244) + 발송 시 토큰 조회·죽은 토큰 정리(#245).
// 트랜잭션 경계를 서비스 레벨에 모은다 — 발송 채널(PushNotificationChannel)은 트랜잭션 밖에서
// 외부 FCM 호출만 하고, 토큰 조회(readOnly)·죽은 토큰 삭제(쓰기)는 이 서비스의 짧은 트랜잭션에 위임한다.
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
        // deviceId·fcmToken 은 유니크 키다. 앞뒤 공백을 입력 경계에서 정규화해야 "x"/"x " 가 다른 키로
        // 갈려 upsert 가 쪼개지거나 죽은 토큰 정리가 빗나가는 일을 막는다. (@NotBlank 는 공백-only 만 거른다)
        val device = deviceId.trim()
        val token = fcmToken.trim()
        val mine = userDeviceRepository.findByUserIdAndDeviceId(userId, device)
        releaseStaleTokenHolder(token, keep = mine)
        mine ?: return userDeviceRepository.save(
            UserDevice(userId = userId, deviceId = device, fcmToken = token),
        )
        mine.refreshToken(token)
        return userDeviceRepository.save(mine)
    }

    // 기기 해제(DELETE, 로그아웃). 멱등 — 이미 없는 기기를 지워도 무해하다.
    @Transactional
    fun unregister(
        userId: UUID,
        deviceId: String,
    ) {
        // 등록과 같은 정규화 — 키 일치를 위해 trim.
        userDeviceRepository.deleteByUserIdAndDeviceId(userId, deviceId.trim())
    }

    // 발송(#245) — 한 사용자의 모든 기기 토큰을 모아 멀티캐스트 대상으로 넘긴다.
    @Transactional(readOnly = true)
    fun findTokens(userId: UUID): List<String> = userDeviceRepository.findAllByUserId(userId).map { it.fcmToken }

    // 발송 후 정리(#245) — FCM 이 UNREGISTERED/INVALID 로 응답한 죽은 토큰을 일괄 제거한다.
    // 발송 결과가 비면(전부 정상) 호출자 분기 없이 여기서 멱등하게 흡수한다.
    @Transactional
    fun removeStaleTokens(fcmTokens: List<String>) {
        if (fcmTokens.isEmpty()) return
        userDeviceRepository.deleteAllByFcmTokenIn(fcmTokens)
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
