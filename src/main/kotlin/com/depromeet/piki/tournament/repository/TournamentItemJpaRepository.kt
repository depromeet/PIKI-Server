package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.domain.TournamentStatus
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TournamentItemJpaRepository : JpaRepository<TournamentItem, Long> {
    fun countByTournamentIdAndDeletedAtIsNull(tournamentId: Long): Int

    fun findByIdAndDeletedAtIsNull(id: Long): TournamentItem?

    @Query("SELECT t FROM TournamentItem t WHERE t.tournamentId = :tournamentId AND t.deletedAt IS NULL ORDER BY t.id ASC")
    fun findAllByTournamentIdAndNotDeleted(@Param("tournamentId") tournamentId: Long): List<TournamentItem>

    @Query("SELECT t FROM TournamentItem t WHERE t.tournamentId IN :tournamentIds AND t.deletedAt IS NULL")
    fun findAllByTournamentIdInAndNotDeleted(@Param("tournamentIds") tournamentIds: List<Long>): List<TournamentItem>

    @Query("SELECT t.id FROM TournamentItem t WHERE t.tournamentId = :tournamentId AND t.deletedAt IS NULL ORDER BY t.id ASC")
    fun findIdsByTournamentId(@Param("tournamentId") tournamentId: Long): List<Long>

    // 이 아이템의 토너먼트 출전 좌표(userId·tournamentId·tournament_item id). 파싱 알림 수신자별 딥링크 라우팅 역조회(#408).
    // adder(userId)별로 자기 토너먼트로 라우팅하므로 userId 까지 함께 뽑아 수신자·라우팅을 한 쿼리로 얻는다.
    // 같은 아이템이 여러 토너먼트에 공유될 수 있어 여러 행이 올 수 있다. id 오름차순으로 결정성을 둔다.
    @Query(
        "SELECT t.userId AS userId, t.tournamentId AS tournamentId, t.id AS tournamentItemId FROM TournamentItem t " +
            "WHERE t.itemId = :itemId AND t.deletedAt IS NULL ORDER BY t.id ASC",
    )
    fun findRoutingByItemId(@Param("itemId") itemId: Long): List<TournamentItemRoutingView>

    @Modifying
    @Query(
        "UPDATE TournamentItem t SET t.deletedAt = :now WHERE t.id = :id AND t.tournamentId = :tournamentId " +
            "AND t.deletedAt IS NULL " +
            "AND EXISTS (SELECT 1 FROM Tournament tour WHERE tour.id = :tournamentId AND tour.status = :status AND tour.deletedAt IS NULL)",
    )
    fun softDeleteIfPending(
        @Param("id") id: Long,
        @Param("tournamentId") tournamentId: Long,
        @Param("status") status: TournamentStatus = TournamentStatus.PENDING,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying
    @Query("UPDATE TournamentItem t SET t.deletedAt = :now WHERE t.tournamentId = :tournamentId AND t.deletedAt IS NULL")
    fun softDeleteAllByTournamentId(@Param("tournamentId") tournamentId: Long, @Param("now") now: LocalDateTime)
}
