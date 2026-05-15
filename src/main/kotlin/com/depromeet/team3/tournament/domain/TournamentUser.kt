package com.depromeet.team3.tournament.domain

import com.depromeet.team3.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

// 어떤 유저가 어떤 토너먼트에 참여했는지를 명시 관리하는 매핑 테이블.
// userId 는 게스트·회원 모두 수용 (현재는 Guest 의 UUID).
@Entity
@Table(name = "tournament_user")
class TournamentUser(
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: Long,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    val userId: UUID,
) : LongBaseEntity()
