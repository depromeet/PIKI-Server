package com.depromeet.team3.wishlist.repository

import com.depromeet.team3.wishlist.domain.Wish
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WishJpaRepository : JpaRepository<Wish, Long> {
    fun countByIdInAndUserId(
        ids: Collection<Long>,
        userId: UUID,
    ): Long

    fun findByUserIdOrderByIdDesc(
        userId: UUID,
        limit: Limit,
    ): List<Wish>

    fun findByUserIdAndIdLessThanOrderByIdDesc(
        userId: UUID,
        id: Long,
        limit: Limit,
    ): List<Wish>
}
