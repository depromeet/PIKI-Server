package com.depromeet.piki.wishlist.repository

import com.depromeet.piki.wishlist.domain.Wish
import com.depromeet.piki.wishlist.domain.WishCursor
import java.util.UUID

interface WishRepository {
    fun save(wish: Wish): Wish

    fun countByIdsAndUserId(
        ids: List<Long>,
        userId: UUID,
    ): Long

    fun countByItemIdsAndUserId(
        itemIds: List<Long>,
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

    // 삭제되지 않은 위시들을 id 목록으로 조회. 존재하는 것만 반환하므로(없는 id 는 빠진다)
    // 호출부가 반환 개수로 누락 여부를 판단한다.
    fun findAllByIds(ids: List<Long>): List<Wish>
}
