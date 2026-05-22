package com.depromeet.team3.wishlist.repository

import com.depromeet.team3.wishlist.domain.Wish
import com.depromeet.team3.wishlist.domain.WishCursor
import java.util.UUID

interface WishRepository {
    fun save(wish: Wish): Wish

    fun countByIdsAndUserId(
        ids: List<Long>,
        userId: UUID,
    ): Long

    // id desc 정렬로 최대 limit 건. cursor 가 있으면 그 id 미만만(다음 페이지). 삭제된 행은 제외.
    fun findPage(
        userId: UUID,
        cursor: WishCursor?,
        limit: Int,
    ): List<Wish>

    // 삭제되지 않은 단건 조회. 없으면 null.
    fun findById(id: Long): Wish?
}
