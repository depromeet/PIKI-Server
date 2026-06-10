package com.depromeet.piki.wishlist.domain

// 위시리스트 조회 페이지 크기. 미지정이면 기본값, 범위를 벗어나면 양 끝으로 보정한다.
// size 는 우리가 한도를 정하는 값이라 거부보다 보정이 클라이언트에 관대하다.
@JvmInline
value class WishlistSize private constructor(
    val value: Int,
) {
    companion object {
        const val DEFAULT = 20
        const val MIN = 1
        const val MAX = 50

        fun of(raw: Int?): WishlistSize = WishlistSize((raw ?: DEFAULT).coerceIn(MIN, MAX))
    }
}
