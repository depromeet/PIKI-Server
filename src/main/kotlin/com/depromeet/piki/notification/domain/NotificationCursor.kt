package com.depromeet.piki.notification.domain

// 알림 히스토리 cursor 페이지네이션의 커서. 직전 페이지 마지막 알림의 id 를 담는다.
// 응답에서 String 으로 내려가고 다음 요청에 그대로 돌아오므로 입력 형식 분기를 여기서 흡수한다.
@JvmInline
value class NotificationCursor private constructor(
    val lastNotificationId: Long,
) {
    companion object {
        // 비었으면 첫 페이지(null). 숫자로 못 바꾸면 우리가 준 적 없는 값 → 계약 위반(400).
        fun parse(raw: String?): NotificationCursor? {
            if (raw.isNullOrBlank()) return null
            val id = raw.trim().toLongOrNull() ?: throw NotificationException.invalidCursor()
            return NotificationCursor(id)
        }
    }
}
