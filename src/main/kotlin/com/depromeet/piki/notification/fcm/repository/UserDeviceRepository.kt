package com.depromeet.piki.notification.fcm.repository

import com.depromeet.piki.notification.fcm.domain.UserDevice
import java.util.UUID

// 사용자 기기(FCM 토큰) 저장소. 토큰 등록/해제(#244)와 발송 시 토큰 조회·정리(#245)가 사용한다.
interface UserDeviceRepository {
    fun save(userDevice: UserDevice): UserDevice

    // upsert(#244) — 토큰은 전역 유일이라 토큰으로 기존 기기 row 를 찾아 현재 사용자로 재배정한다.
    fun findByFcmToken(fcmToken: String): UserDevice?

    // upsert(#244) — 같은 기기의 토큰 회전을 같은 row 갱신으로 흡수하기 위해 기기 단위로 찾는다.
    fun findByUserIdAndDeviceId(userId: UUID, deviceId: String): UserDevice?

    // 발송(#245) — 한 사용자의 모든 기기로 멀티캐스트하기 위해 토큰을 모은다.
    fun findAllByUserId(userId: UUID): List<UserDevice>

    // 기기 해제(#244 로그아웃) — 본인 기기만 지우도록 userId 와 함께 조건.
    fun deleteByUserIdAndDeviceId(userId: UUID, deviceId: String)

    // 발송 후 정리(#245) — FCM 이 UNREGISTERED/INVALID 로 응답한 죽은 토큰을 일괄 제거한다.
    fun deleteAllByFcmTokenIn(fcmTokens: Collection<String>)

    // 토큰 탈취(#244 reconcile) — 다른 사용자가 들고 있던 토큰 row 를 제거한다.
    fun delete(userDevice: UserDevice)

    // DELETE 후 flush — UNIQUE(fcm_token) 충돌을 막기 위해 삭제를 후속 save 전에 DB 로 내린다(Hibernate flush 순서 함정 회피).
    fun flush()
}
