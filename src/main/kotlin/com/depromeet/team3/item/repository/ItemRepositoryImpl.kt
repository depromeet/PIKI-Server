package com.depromeet.team3.item.repository

import com.depromeet.team3.item.domain.Item
import org.springframework.stereotype.Repository

@Repository
class ItemRepositoryImpl(
    private val itemJpaRepository: ItemJpaRepository,
) : ItemRepository {
    override fun save(item: Item): Item = itemJpaRepository.save(item)
}
