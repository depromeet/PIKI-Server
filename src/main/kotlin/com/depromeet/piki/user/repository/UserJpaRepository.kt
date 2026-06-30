package com.depromeet.piki.user.repository

import com.depromeet.piki.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserJpaRepository : JpaRepository<User, UUID> {
    fun existsByNickname(nickname: String): Boolean

    fun existsByNicknameAndIdNot(
        nickname: String,
        id: UUID,
    ): Boolean

    @Query("SELECT u.nickname FROM User u WHERE u.nickname IN :nicknames")
    fun findNicknamesIn(
        @Param("nicknames") nicknames: Collection<String>,
    ): List<String>

    // 활성(미탈퇴) 유저 id 전체 — 공지 알림센터 fan-out(#560) 대상. tombstone(탈퇴: deleted_at 채워짐)은 제외하고
    // 게스트(identity_type=GUEST)는 포함한다. id 만 projection 해 엔티티를 적재하지 않는다.
    @Query("SELECT u.id FROM User u WHERE u.deletedAt IS NULL")
    fun findActiveIds(): List<UUID>

    // 활성(미탈퇴) 유저 수 — 공지 발송 전 알림센터 대상 인원 미리보기(#560). 게스트 포함.
    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL")
    fun countActive(): Long
}
