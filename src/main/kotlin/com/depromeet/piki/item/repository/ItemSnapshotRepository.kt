package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.ItemSnapshot
import java.time.LocalDateTime

interface ItemSnapshotRepository {
    fun save(snapshot: ItemSnapshot): ItemSnapshot

    fun saveAll(snapshots: List<ItemSnapshot>): List<ItemSnapshot>

    // 한 item 의 최신 snapshot. 2단계는 item 당 snapshot 1행이라 사실상 그 행이며,
    // 상태 전이(markReady/markFailed/recover) 대상을 찾는 데 쓴다. 5단계 갱신에서 여러 버전이 쌓이면 "최신 버전" 의미가 된다.
    fun findLatestByItemId(itemId: Long): ItemSnapshot?

    // 3단계(참조 이전): wish/tournament_item 이 가리키는 고정 snapshot 을 id 로 끌어온다.
    // 단건 조회(위시 단건·토너먼트 아이템 단건)용. 삭제된 행은 제외, 없으면 null.
    fun findById(id: Long): ItemSnapshot?

    // 여러 snapshot 을 id 목록으로 한 번에 조회(목록·결과 조회의 메모리 조인용). 존재하는 것만 반환.
    fun findByIds(ids: List<Long>): List<ItemSnapshot>

    // 디스패처가 집을 PENDING snapshot 을 FIFO 로 batchSize 개, FOR UPDATE 로 잠가 가져온다(outbox claim 대상).
    fun findDuePending(batchSize: Int): List<ItemSnapshot>

    // recover 가 집을 stale PROCESSING snapshot — claim 시각(updated_at)이 threshold 이전인 것 batchSize 개, FOR UPDATE.
    fun findStaleProcessing(
        threshold: LocalDateTime,
        batchSize: Int,
    ): List<ItemSnapshot>
}
