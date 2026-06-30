package com.depromeet.piki.notification.fcm.repository

import com.depromeet.piki.notification.fcm.domain.UserDevice
import java.util.UUID

// 사용자 기기(FCM 토큰) 저장소. 토큰 등록/해제(#244)와 발송 시 토큰 조회·정리(#245)가 사용한다.
interface UserDeviceRepository {
    fun save(userDevice: UserDevice): UserDevice

    // 토큰 보유자(앱 푸시 수신 가능 유저) 수 — 공지의 "그중 푸시 N명" 표시(#560). 알림센터 fan-out 대상은 활성 유저 전체.
    fun countTokenHolders(): Long

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

    // 토큰 탈취(#244 reconcile, #396 후속) — holder row 를 PK 로 멱등 삭제한다. bulk delete 라 0건이어도 무해 —
    // 동시 등록으로 다른 트랜잭션이 같은 holder 를 먼저 지운 경쟁(StaleStateException)을 원천 차단한다.
    fun deleteByIdBulk(id: Long)

    // 탈퇴 cascade — 그 유저의 모든 기기(FCM 토큰)를 하드삭제한다.
    fun deleteAllByUserId(userId: UUID)
}
