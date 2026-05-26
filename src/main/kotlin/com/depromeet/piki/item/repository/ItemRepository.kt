package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.Item
import java.time.LocalDateTime

interface ItemRepository {
    fun save(item: Item): Item

    fun saveAll(items: List<Item>): List<Item>

    fun findByIds(ids: List<Long>): List<Item>

    fun findById(id: Long): Item?

    // cutoff 이전에 생성됐는데 아직 PROCESSING 인 item — 워커가 죽어 방치된 stale 후보의 id.
    fun findStaleProcessingIds(cutoff: LocalDateTime): List<Long>
}
