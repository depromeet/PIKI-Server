package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentItem
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class TournamentItemRepositoryImpl(
    private val tournamentItemJpaRepository: TournamentItemJpaRepository,
) : TournamentItemRepository {
    override fun saveAll(items: List<TournamentItem>): List<TournamentItem> = tournamentItemJpaRepository.saveAll(items)

    override fun countByTournamentId(tournamentId: Long): Int = tournamentItemJpaRepository.countByTournamentId(tournamentId)

    override fun findAllByTournamentId(tournamentId: Long): List<TournamentItem> =
        tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId)

    override fun findById(id: Long): TournamentItem? = tournamentItemJpaRepository.findByIdOrNull(id)

    override fun delete(tournamentItem: TournamentItem) = tournamentItemJpaRepository.delete(tournamentItem)

    override fun deleteIfPending(
        id: Long,
        tournamentId: Long,
    ): Int = tournamentItemJpaRepository.deleteIfPending(id, tournamentId)
}
