package com.depromeet.piki.notification.domain

// 알림 히스토리 조회 페이지 크기. 미지정이면 기본값, 범위를 벗어나면 양 끝으로 보정한다.
// size 는 우리가 한도를 정하는 값이라 거부보다 보정이 클라이언트에 관대하다. (WishlistSize 와 동일 정책)
@JvmInline
value class NotificationHistorySize private constructor(
    val value: Int,
) {
    companion object {
        const val DEFAULT = 20
        const val MIN = 1
        const val MAX = 50

        fun of(raw: Int?): NotificationHistorySize = NotificationHistorySize((raw ?: DEFAULT).coerceIn(MIN, MAX))
    }
}
