package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.notification.fcm.domain.UserDevice
import com.depromeet.piki.notification.fcm.repository.UserDeviceRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// FCM 기기 토큰 등록/해제(#244) + 발송 시 토큰 조회·죽은 토큰 정리(#245).
// 트랜잭션 경계를 서비스 레벨에 모은다 — 발송 채널(PushNotificationChannel)은 트랜잭션 밖에서
// 외부 FCM 호출만 하고, 토큰 조회(readOnly)·죽은 토큰 삭제(쓰기)는 이 서비스의 짧은 트랜잭션에 위임한다.
@Service
class UserDeviceService(
    private val userDeviceRepository: UserDeviceRepository,
    private val userDeviceWriter: UserDeviceWriter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 토큰 등록/갱신(POST). 실제 upsert 는 UserDeviceWriter 의 짧은 트랜잭션에 위임하고, 여기선 동시 등록
    // 충돌만 흡수한다(#396) — 같은 fcmToken 으로 동시 등록이 들어오면 둘 다 "없음"을 보고 INSERT 해 한쪽이
    // UNIQUE 충돌(DataIntegrityViolationException)로 깨진다. 충돌을 일으킨 row 는 이미 다른 트랜잭션이
    // 커밋했으므로, 새 트랜잭션으로 1회 재시도하면 upsert 가 그 row 를 찾아 갱신(또는 토큰 재배정)으로 수렴한다.
    //
    // register 에 @Transactional 을 두지 않는 게 핵심이다 — 두면 writer.upsert 가 같은 트랜잭션에 합류(REQUIRED)해,
    // 첫 시도의 충돌이 트랜잭션을 rollback-only 로 오염시켜 재시도가 같은 트랜잭션 안에서 또 깨진다. writer 를
    // 별도 빈으로 두고 proxy 를 거쳐 호출해야 매 시도가 독립 트랜잭션이 된다(self-invocation 회피).
    fun register(
        userId: UUID,
        deviceId: String,
        fcmToken: String,
    ): UserDevice =
        try {
            userDeviceWriter.upsert(userId, deviceId, fcmToken)
        } catch (e: DataIntegrityViolationException) {
            log.warn(
                "FCM 토큰 등록 동시 충돌 — 새 트랜잭션으로 1회 재시도 userId={} deviceId={} reason={}",
                userId,
                deviceId.trim(),
                e.javaClass.simpleName,
            )
            userDeviceWriter.upsert(userId, deviceId, fcmToken)
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

    // 토큰 보유자(앱 푸시 수신 가능 유저) 수 — 백오피스 공지의 "그중 푸시 N명" 표시(#391/#560).
    // 공지 알림센터 fan-out 자체는 활성 유저 전체이고, 이 수는 그중 실제 FCM 이 도달하는 부분집합이다.
    @Transactional(readOnly = true)
    fun countTokenHolders(): Long = userDeviceRepository.countTokenHolders()

    // 발송 후 정리(#245) — FCM 이 UNREGISTERED/INVALID 로 응답한 죽은 토큰을 일괄 제거한다.
    // 발송 결과가 비면(전부 정상) 호출자 분기 없이 여기서 멱등하게 흡수한다.
    @Transactional
    fun removeStaleTokens(fcmTokens: List<String>) {
        if (fcmTokens.isEmpty()) return
        userDeviceRepository.deleteAllByFcmTokenIn(fcmTokens)
    }
}
