package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.Item

interface ItemRepository {
    fun save(item: Item): Item

    fun findByIds(ids: List<Long>): List<Item>

    fun findById(id: Long): Item?
}
