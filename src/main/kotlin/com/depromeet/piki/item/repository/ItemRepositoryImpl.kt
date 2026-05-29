package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.Item
import com.depromeet.piki.item.domain.ItemStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ItemRepositoryImpl(
    private val itemJpaRepository: ItemJpaRepository,
) : ItemRepository {
    override fun save(item: Item): Item = itemJpaRepository.save(item)

    override fun saveAll(items: List<Item>): List<Item> = itemJpaRepository.saveAll(items)

    override fun findByIds(ids: List<Long>): List<Item> = itemJpaRepository.findAllById(ids)

    override fun findById(id: Long): Item? = itemJpaRepository.findById(id).orElse(null)

    override fun findRecent(limit: Int): List<Item> = itemJpaRepository.findRecent(PageRequest.of(0, limit))

    override fun findStaleProcessingIds(cutoff: LocalDateTime): List<Long> =
        itemJpaRepository.findIdsByStatusAndCreatedAtBefore(ItemStatus.PROCESSING, cutoff)
}
