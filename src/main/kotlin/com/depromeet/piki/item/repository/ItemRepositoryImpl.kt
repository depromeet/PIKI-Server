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

    override fun findRecent(limit: Int): List<Item> {
        // PageRequest.of 는 size > 0 을 요구한다. 호출부(AdminItemService)가 coerce 하지만, 리포지토리를
        // 직접 재사용하는 경로에서도 IllegalArgumentException 으로 깨지지 않게 여기서도 막는다.
        require(limit > 0) { "limit 은 1 이상이어야 한다: $limit" }
        return itemJpaRepository.findRecent(PageRequest.of(0, limit))
    }

    override fun findStaleProcessingIds(cutoff: LocalDateTime): List<Long> =
        itemJpaRepository.findIdsByStatusAndCreatedAtBefore(ItemStatus.PROCESSING, cutoff)
}
