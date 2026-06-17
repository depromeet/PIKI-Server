package com.depromeet.piki.notification.service

import java.util.UUID

// 공지 한 수신자(userId)의 전송 결과(#489). 브로드캐스터가 유저 단위로 만들어 호출자(admin)에게 흘려보내고,
// admin 이 announcement_deliveries 건별 행으로 영속화한다(추후 클릭 analytics 의 기반).
data class RecipientDelivery(
    val userId: UUID,
    val status: DeliveryStatus,
    // 실패(FAILED) 시 대표 FCM 에러코드(UNREGISTERED·SENDER_ID_MISMATCH 등). 그 외는 null.
    val fcmCode: String?,
)

// SUCCESS: 1개 이상 기기 도달 · FAILED: 토큰은 있으나 전 기기 실패 · NO_TOKEN: 토큰 없음 · SKIPPED: FCM 미설정 환경(로컬)
enum class DeliveryStatus { SUCCESS, FAILED, NO_TOKEN, SKIPPED }
