package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.common.logging.SensitiveData
import com.depromeet.piki.notification.fcm.domain.UserDevice
import com.depromeet.piki.notification.fcm.repository.UserDeviceRepository
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

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
        // 토큰 원문은 크리덴셜이라 지문으로만. deviceId 는 기기 식별자라 그대로(같은 기기의 등록/해제 상관추적 키).
        mine ?: run {
            log.info("FCM 토큰 등록(신규) userId={} deviceId={} token={}", userId, device, SensitiveData.maskToken(token))
            return userDeviceRepository.save(UserDevice(userId = userId, deviceId = device, fcmToken = token))
        }
        log.info("FCM 토큰 등록(갱신) userId={} deviceId={} token={}", userId, device, SensitiveData.maskToken(token))
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
        val device = deviceId.trim()
        userDeviceRepository.deleteByUserIdAndDeviceId(userId, device)
        log.info("FCM 토큰 해제 userId={} deviceId={}", userId, device)
    }

    // 발송(#245) — 한 사용자의 모든 기기 토큰을 모아 멀티캐스트 대상으로 넘긴다.
    @Transactional(readOnly = true)
    fun findTokens(userId: UUID): List<String> = userDeviceRepository.findAllByUserId(userId).map { it.fcmToken }

    // 토큰 보유자(앱 푸시 수신 가능 유저) 수 — 백오피스 공지 발송 대상 인원 표시(#391).
    @Transactional(readOnly = true)
    fun countTokenHolders(): Long = userDeviceRepository.countTokenHolders()

    // 토큰 보유 유저 전체의 식별자(#489) — 공지 브로드캐스트가 이 id 들을 돌며 유저별 알림 생성·발송한다.
    @Transactional(readOnly = true)
    fun findAllTokenHolderIds(): List<UUID> = userDeviceRepository.findAllTokenHolderIds()

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
