package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.domain.ItemStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ItemSnapshotJpaRepository : JpaRepository<ItemSnapshot, Long> {
    // 한 item 의 살아있는(soft-delete 안 된) 최신 snapshot 1개 — id 역순 첫 행.
    fun findFirstByItemIdAndDeletedAtIsNullOrderByIdDesc(itemId: Long): ItemSnapshot?

    // 살아있는 단건 조회. JpaRepository.findById(Optional) 와 충돌하지 않도록 deletedAt 조건을 붙여 이름을 구분한다.
    fun findByIdAndDeletedAtIsNull(id: Long): ItemSnapshot?

    // 살아있는 행만 id 목록으로 일괄 조회.
    fun findByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<ItemSnapshot>

    // cutoff 이전에 생성됐는데 아직 PROCESSING 인 버전의 itemId — 워커가 죽어 방치된 stale 후보. 전이는 itemId 로 한다.
    @Query("select s.itemId from ItemSnapshot s where s.status = :status and s.createdAt < :cutoff and s.deletedAt is null")
    fun findItemIdsByStatusAndCreatedAtBefore(
        @Param("status") status: ItemStatus,
        @Param("cutoff") cutoff: LocalDateTime,
    ): List<Long>
}
