package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.ItemSnapshot

interface ItemSnapshotRepository {
    fun save(snapshot: ItemSnapshot): ItemSnapshot

    fun saveAll(snapshots: List<ItemSnapshot>): List<ItemSnapshot>

    // 한 item 의 최신 snapshot. 2단계는 item 당 snapshot 1행이라 사실상 그 행이며,
    // 상태 전이(markReady/markFailed/recover) 대상을 찾는 데 쓴다. 5단계 갱신에서 여러 버전이 쌓이면 "최신 버전" 의미가 된다.
    fun findLatestByItemId(itemId: Long): ItemSnapshot?
}
