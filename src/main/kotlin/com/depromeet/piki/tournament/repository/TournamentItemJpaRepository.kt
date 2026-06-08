package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.TournamentItem
import com.depromeet.piki.tournament.domain.TournamentStatus
import java.time.LocalDateTime
import java.util.UUID
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

    // 이 아이템을 토너먼트에 추가한 사람들(adder). 같은 아이템이 여러 토너먼트에 공유될 수 있어 DISTINCT. (파싱 알림 수신자 역조회)
    @Query("SELECT DISTINCT t.userId FROM TournamentItem t WHERE t.itemId = :itemId AND t.deletedAt IS NULL")
    fun findUserIdsByItemId(@Param("itemId") itemId: Long): List<UUID>

    // 이 아이템의 토너먼트 출전 좌표(tournamentId·tournament_item id). 파싱 알림 딥링크 라우팅 역조회(#408).
    // 공유 대비 List 지만 파싱 시점엔 단일 컨텍스트라 0~1 행이다. id 오름차순으로 결정성만 둔다.
    @Query(
        "SELECT t.tournamentId AS tournamentId, t.id AS tournamentItemId FROM TournamentItem t " +
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
    fun softDeleteAllByTournamentId(
        @Param("tournamentId") tournamentId: Long,
        @Param("now") now: LocalDateTime,
    )
}
