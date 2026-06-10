package com.depromeet.piki.notification.controller.dto

import com.depromeet.piki.notification.domain.NotificationCategory
import io.swagger.v3.oas.annotations.media.Schema

// 읽음 처리 응답 — 처리 후 안읽음 수(전체·탭별)를 서버 권위 값으로 내려, 클라가 +1/-1 산수 없이 그대로 미러링하게 한다.
// 멀티 디바이스에서도 읽은 기기는 추가 조회 없이 최신 badge 를 같은 왕복에서 받는다(다른 기기는 재진입 시 GET /notifications 로 보정).
// list 응답과 같은 셰입(unreadCount + unreadCountByCategory)이라, 읽음 후 앱 badge 와 활동/시스템 탭 badge 를 한 번에 갱신한다.
@Schema(description = "알림 읽음 처리 응답 — 처리 후 안읽음 수(전체·탭별)")
data class NotificationReadResponse(
    @field:Schema(description = "처리 후 본인 전체 안읽음 수 (앱 badge). 클라는 이 값을 그대로 badge 로 미러링한다", example = "2")
    val unreadCount: Long,
    @field:Schema(description = "처리 후 카테고리별 안읽음 수 (탭 badge). 모든 카테고리 키 포함, 없으면 0", example = "{\"ACTIVITY\":1,\"SYSTEM\":1}")
    val unreadCountByCategory: Map<NotificationCategory, Long>,
) {
    companion object {
        // total 은 카테고리 합으로 도출 — 전체·탭별 두 수치가 어긋날 여지를 없앤다(getHistory 와 동일 규칙).
        fun of(unreadCountByCategory: Map<NotificationCategory, Long>): NotificationReadResponse =
            NotificationReadResponse(
                unreadCount = unreadCountByCategory.values.sum(),
                unreadCountByCategory = unreadCountByCategory,
            )
    }
}
