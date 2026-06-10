package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.ItemSnapshot
import com.depromeet.piki.item.domain.ItemStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
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

    // 디스패처가 집을 작업(PENDING) snapshot 을 FIFO(created_at)로 limit 개, FOR UPDATE 로 잠가 가져온다.
    // 락으로 같은 행을 두 디스패처가 동시에 claim 하는 것을 막는다(멀티 인스턴스 대비). limit 은 Pageable 로 주입.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ItemSnapshot s where s.status = :status and s.deletedAt is null order by s.createdAt asc, s.id asc")
    fun findByStatusForUpdate(
        @Param("status") status: ItemStatus,
        pageable: Pageable,
    ): List<ItemSnapshot>

    // recover 가 집을 stale 작업 — updated_at(claim 시각)이 threshold 이전인 PROCESSING snapshot 을 limit 개, FOR UPDATE.
    // created_at 이 아니라 updated_at 기준이라, PENDING 으로 오래 갇혔다 방금 claim 된 행은 stale 로 오판하지 않는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select s from ItemSnapshot s where s.status = :status and s.updatedAt < :threshold and s.deletedAt is null " +
            "order by s.updatedAt asc, s.id asc",
    )
    fun findStaleByStatusForUpdate(
        @Param("status") status: ItemStatus,
        @Param("threshold") threshold: LocalDateTime,
        pageable: Pageable,
    ): List<ItemSnapshot>
}
