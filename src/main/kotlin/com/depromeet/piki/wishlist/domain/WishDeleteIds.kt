package com.depromeet.piki.wishlist.domain

// 다중 삭제 대상 위시 id 목록. 중복은 같은 위시를 가리킬 뿐이라 distinct 로 정규화하고,
// 비어 있거나 상한(MAX_COUNT)을 넘으면 계약 위반이라 400 으로 거부한다.
// WishlistSize 와 달리 보정하지 않는다 — "무엇을 지울지"는 클라이언트가 명시해야 하는 값이라
// 빈/초과를 조용히 깎으면 의도와 다른 삭제가 일어날 수 있다.
@JvmInline
value class WishDeleteIds private constructor(
    val values: List<Long>,
) {
    companion object {
        // 한 번에 삭제 가능한 상한. query param(?ids=) 으로 받으므로 URL 길이도 함께 고려한 값이다
        // (100개 × 천만대 id ≈ 1KB 미만으로 URL 한계에 여유가 크다).
        const val MAX_COUNT = 100

        fun of(rawIds: List<Long>): WishDeleteIds {
            val distinct = rawIds.distinct()
            if (distinct.isEmpty() || distinct.size > MAX_COUNT) throw WishException.invalidIdCount()
            return WishDeleteIds(distinct)
        }
    }
}
