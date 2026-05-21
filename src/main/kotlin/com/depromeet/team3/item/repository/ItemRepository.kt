package com.depromeet.team3.item.repository

import com.depromeet.team3.item.domain.Item

interface ItemRepository {
    fun save(item: Item): Item
}
