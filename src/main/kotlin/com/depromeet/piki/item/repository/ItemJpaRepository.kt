package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.Item
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ItemJpaRepository : JpaRepository<Item, Long> {
    // admin 조회 도구용. 삭제되지 않은 item 을 createdAt 내림차순으로, limit 은 Pageable 로 동적 주입.
    // 동률(같은 createdAt)에서 LIMIT 결과가 요청마다 달라지지 않도록 id 를 보조 정렬 키로 둔다.
    @Query("select i from Item i where i.deletedAt is null order by i.createdAt desc, i.id desc")
    fun findRecent(pageable: Pageable): List<Item>
}
