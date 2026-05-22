package com.depromeet.team3.item.repository

import com.depromeet.team3.item.domain.Item
import org.springframework.stereotype.Repository

@Repository
class ItemRepositoryImpl(
    private val itemJpaRepository: ItemJpaRepository,
) : ItemRepository {
    override fun save(item: Item): Item = itemJpaRepository.save(item)

    override fun findByIds(ids: List<Long>): List<Item> = itemJpaRepository.findAllById(ids)

    override fun findById(id: Long): Item? = itemJpaRepository.findById(id).orElse(null)
}
