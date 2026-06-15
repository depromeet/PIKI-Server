package com.depromeet.piki.notification.fcm.service

// FCM 멀티캐스트 발송 결과(#489). 기존엔 죽은 토큰 리스트만 반환했으나, 공지 브로드캐스트가
// 수신자별 전송 성공/실패와 사유를 건별 기록·집계할 수 있도록 결과를 구조화해 올려보낸다.
// - staleTokens: 정리 대상(UNREGISTERED) 토큰. 호출자가 삭제한다(기존 계약 유지).
// - successCount: 발송 성공한 토큰 수.
// - failureByCode: 실패 토큰을 FCM messagingErrorCode 이름(UNREGISTERED·SENDER_ID_MISMATCH 등)별로 센 맵.
data class FcmSendResult(
    val staleTokens: List<String>,
    val successCount: Int,
    val failureByCode: Map<String, Int>,
) {
    // 시도한 토큰 수(성공 + 실패). 토큰 전체와 같지만, 청크 통째 실패(네트워크)로 응답이 없으면 그만큼 줄 수 있다.
    val attemptedCount: Int get() = successCount + failureByCode.values.sum()

    // 전 토큰 실패 시 대표 실패 코드 — 가장 많이 나온 코드. 수신자 건별 행의 fcm_code 로 쓴다.
    val dominantFailureCode: String? get() = failureByCode.maxByOrNull { it.value }?.key

    companion object {
        val EMPTY = FcmSendResult(emptyList(), 0, emptyMap())
    }
}
