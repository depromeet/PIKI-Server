package com.depromeet.piki.item.repository

import com.depromeet.piki.item.domain.ItemSnapshot
import org.springframework.stereotype.Repository

@Repository
class ItemSnapshotRepositoryImpl(
    private val itemSnapshotJpaRepository: ItemSnapshotJpaRepository,
) : ItemSnapshotRepository {
    override fun save(snapshot: ItemSnapshot): ItemSnapshot = itemSnapshotJpaRepository.save(snapshot)

    override fun saveAll(snapshots: List<ItemSnapshot>): List<ItemSnapshot> =
        itemSnapshotJpaRepository.saveAll(snapshots)

    override fun findLatestByItemId(itemId: Long): ItemSnapshot? =
        itemSnapshotJpaRepository.findFirstByItemIdAndDeletedAtIsNullOrderByIdDesc(itemId)

    override fun findById(id: Long): ItemSnapshot? = itemSnapshotJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByIds(ids: List<Long>): List<ItemSnapshot> =
        itemSnapshotJpaRepository.findByIdInAndDeletedAtIsNull(ids)
}
