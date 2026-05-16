package com.depromeet.team3.wishlist.repository

import com.depromeet.team3.product.domain.ProductLink
import com.depromeet.team3.wishlist.domain.Wish
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WishJpaRepository : JpaRepository<Wish, Long> {
    // product 가 @Embedded 라 derivation 파서가 productLink → product.link 로
    // 폴백 resolve 하는 형태로 동작은 하나, 의존하기엔 모호해 JPQL 로 명시한다.
    @Query("select count(w) > 0 from Wish w where w.userId = :userId and w.product.link = :link")
    fun existsByUserIdAndProductLink(
        @Param("userId") userId: UUID,
        @Param("link") link: ProductLink,
    ): Boolean

    fun countByIdInAndUserId(
        ids: Collection<Long>,
        userId: UUID,
    ): Long
}
