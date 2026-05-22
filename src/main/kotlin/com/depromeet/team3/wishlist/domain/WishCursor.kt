package com.depromeet.team3.wishlist.domain

import com.depromeet.team3.wishlist.service.WishException

// 위시리스트 cursor 페이지네이션의 커서. 직전 페이지 마지막 wish 의 id 를 담는다.
// 응답에서 String 으로 내려가고 다음 요청에 그대로 돌아오므로 입력 형식 분기를 여기서 흡수한다.
@JvmInline
value class WishCursor private constructor(
    val lastWishId: Long,
) {
    companion object {
        // 비었으면 첫 페이지(null). 숫자로 못 바꾸면 우리가 준 적 없는 값 → 계약 위반(400).
        fun parse(raw: String?): WishCursor? {
            if (raw.isNullOrBlank()) return null
            val id = raw.trim().toLongOrNull() ?: throw WishException.invalidCursor()
            return WishCursor(id)
        }
    }
}
