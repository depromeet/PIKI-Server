package com.depromeet.piki.tournament.repository

import com.depromeet.piki.tournament.domain.Tournament
import com.depromeet.piki.tournament.domain.TournamentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface TournamentJpaRepository : JpaRepository<Tournament, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Tournament?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tournament t WHERE t.id = :id AND t.deletedAt IS NULL")
    fun findByIdForUpdate(id: Long): Tournament?

    fun findByIdInAndStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(
        ids: List<Long>,
        statuses: List<TournamentStatus>,
    ): List<Tournament>

    fun findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(ids: List<Long>): List<Tournament>

    fun findBySourceTournamentIdAndDeletedAtIsNull(sourceTournamentId: Long): List<Tournament>

    // findBy 는 결과가 2개 이상이면 IncorrectResultSizeDataAccessException → 500 이므로
    // findFirst 로 방어한다. uk_tournaments_active_invite_code 가 정상이면 활성 코드 중복은 없지만
    // 마이그레이션 이전 레거시 데이터 등 예외 상황에서도 안전하게 동작한다.
    fun findFirstByInviteCodeAndDeletedAtIsNull(inviteCode: String): Tournament?

    fun existsByInviteCodeAndDeletedAtIsNull(inviteCode: String): Boolean
}
