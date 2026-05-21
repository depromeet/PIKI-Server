package com.depromeet.team3.tournament.repository

import com.depromeet.team3.tournament.domain.TournamentItem
import org.springframework.stereotype.Repository

@Repository
class TournamentItemRepositoryImpl(
    private val tournamentItemJpaRepository: TournamentItemJpaRepository,
) : TournamentItemRepository {
    override fun save(tournamentItem: TournamentItem): TournamentItem = tournamentItemJpaRepository.save(tournamentItem)

    override fun findAllByTournamentId(tournamentId: Long): List<TournamentItem> =
        tournamentItemJpaRepository.findAllByTournamentIdOrderByIdAsc(tournamentId)
}
