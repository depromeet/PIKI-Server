package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentItem
import java.time.LocalDateTime
import org.springframework.stereotype.Repository

@Repository
class TournamentItemRepositoryImpl(
    private val tournamentItemJpaRepository: TournamentItemJpaRepository,
) : TournamentItemRepository {
    override fun saveAll(items: List<TournamentItem>): List<TournamentItem> = tournamentItemJpaRepository.saveAll(items)

    override fun countByTournamentId(tournamentId: Long): Int =
        tournamentItemJpaRepository.countByTournamentIdAndDeletedAtIsNull(tournamentId)

    override fun findIdsByTournamentId(tournamentId: Long): List<Long> =
        tournamentItemJpaRepository.findIdsByTournamentId(tournamentId)

    override fun findAllByTournamentId(tournamentId: Long): List<TournamentItem> =
        tournamentItemJpaRepository.findAllByTournamentIdAndNotDeleted(tournamentId)

    override fun findByIds(ids: List<Long>): List<TournamentItem> = tournamentItemJpaRepository.findAllById(ids)

    override fun findById(id: Long): TournamentItem? = tournamentItemJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun delete(tournamentItem: TournamentItem) = tournamentItemJpaRepository.delete(tournamentItem)

    override fun softDeleteIfPending(
        id: Long,
        tournamentId: Long,
    ): Int = tournamentItemJpaRepository.softDeleteIfPending(id, tournamentId, now = LocalDateTime.now())

    override fun softDeleteAllByTournamentId(tournamentId: Long) {
        tournamentItemJpaRepository.softDeleteAllByTournamentId(tournamentId, LocalDateTime.now())
    }
}
