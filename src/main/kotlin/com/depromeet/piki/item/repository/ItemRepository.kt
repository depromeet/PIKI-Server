package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.Item

interface ItemRepository {
    fun save(item: Item): Item

    fun saveAll(items: List<Item>): List<Item>

    fun findByIds(ids: List<Long>): List<Item>

    fun findById(id: Long): Item?

    // admin 운영 도구의 조회용 — 삭제되지 않은 item 을 createdAt 내림차순으로 limit 개.
    fun findRecent(limit: Int): List<Item>
}
